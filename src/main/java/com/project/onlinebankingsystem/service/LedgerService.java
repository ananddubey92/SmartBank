package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.LedgerEntry;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.LedgerEntryRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuditLogService auditLogService;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository,
                         TransactionRepository transactionRepository,
                         AccountRepository accountRepository,
                         AuditLogService auditLogService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Records a single credit entry (e.g. deposit, refund) with updated running balance.
     */
    @Transactional
    public LedgerEntry recordCredit(Account account, BigDecimal amount, String txRef, String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionReference(txRef)
                .account(account)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .runningBalance(account.getBalance())
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Records a single debit entry (e.g. cash withdrawal) with updated running balance.
     */
    @Transactional
    public LedgerEntry recordDebit(Account account, BigDecimal amount, String txRef, String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionReference(txRef)
                .account(account)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .runningBalance(account.getBalance())
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();
        return ledgerEntryRepository.save(entry);
    }

    /**
     * Records a balanced double-entry pair: DEBIT on source, CREDIT on destination.
     * Enforces equal debit and credit amounts.
     */
    @Transactional
    public void recordTransferPair(Account source, Account destination, BigDecimal amount, String txRef, String description) {
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionReference(txRef)
                .account(source)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .runningBalance(source.getBalance())
                .description(description != null ? description : "Fund Transfer Out")
                .timestamp(LocalDateTime.now())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionReference(txRef)
                .account(destination)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .runningBalance(destination.getBalance())
                .description(description != null ? description : "Fund Transfer In")
                .timestamp(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        log.info("Recorded balanced ledger transfer pair [Ref: {}] Amount: ₹{} between Source: {} and Dest: {}",
                txRef, amount, source.getAccountNumber(), destination.getAccountNumber());
    }

    /**
     * Validates reconciliation: whether recorded credits minus debits align with ledger consistency.
     */
    @Transactional(readOnly = true)
    public boolean reconcileAccount(Account account) {
        BigDecimal totalCredits = ledgerEntryRepository.sumCreditsForAccount(account);
        BigDecimal totalDebits = ledgerEntryRepository.sumDebitsForAccount(account);
        BigDecimal calculated = totalCredits.subtract(totalDebits);

        log.info("Reconciliation for Account #{}: Credits = ₹{}, Debits = ₹{}, Net = ₹{}, Account Balance = ₹{}",
                account.getAccountNumber(), totalCredits, totalDebits, calculated, account.getBalance());

        return calculated.compareTo(account.getBalance()) == 0;
    }

    /**
     * Safely reverses a completed transaction by issuing counter-balancing ledger entries
     * and marking the original transaction REVERSED.
     */
    @Transactional
    public Transaction reverseTransaction(String txRef, String performedBy, String ipAddress, String reason) {
        Transaction tx = transactionRepository.findByTransactionReference(txRef)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for reference: " + txRef));

        if (tx.getStatus() != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED transactions can be reversed. Current status: " + tx.getStatus());
        }

        String revTxRef = "REV-" + txRef;
        BigDecimal amount = tx.getAmount();

        Account src = tx.getSourceAccount();
        Account dst = tx.getDestinationAccount();

        if (src != null && dst != null) {
            // Transfer reversal: Credit source, Debit destination
            if (dst.getBalance().compareTo(amount) < 0) {
                throw new IllegalStateException("Cannot reverse transfer: Destination account has insufficient balance (₹" + dst.getBalance() + ") to refund ₹" + amount);
            }
            dst.setBalance(dst.getBalance().subtract(amount));
            src.setBalance(src.getBalance().add(amount));

            accountRepository.save(dst);
            accountRepository.save(src);

            recordDebit(dst, amount, revTxRef, "Reversal Debit for " + txRef + ": " + reason);
            recordCredit(src, amount, revTxRef, "Reversal Credit for " + txRef + ": " + reason);
        } else if (dst != null) {
            // Deposit reversal: Debit destination
            if (dst.getBalance().compareTo(amount) < 0) {
                throw new IllegalStateException("Cannot reverse deposit: Account has insufficient balance to reverse ₹" + amount);
            }
            dst.setBalance(dst.getBalance().subtract(amount));
            accountRepository.save(dst);
            recordDebit(dst, amount, revTxRef, "Reversal Debit for Deposit " + txRef);
        } else if (src != null) {
            // Withdrawal reversal: Credit source
            src.setBalance(src.getBalance().add(amount));
            accountRepository.save(src);
            recordCredit(src, amount, revTxRef, "Reversal Credit for Withdrawal " + txRef);
        }

        tx.setStatus(TransactionStatus.FAILED);
        tx.setDescription(tx.getDescription() + " [REVERSED: " + reason + "]");
        Transaction updated = transactionRepository.save(tx);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "TRANSACTION_REVERSAL",
                "TRANSACTION",
                txRef,
                TransactionStatus.COMPLETED.name(),
                TransactionStatus.FAILED.name(),
                ipAddress,
                "SUCCESS",
                String.format("Reversed transaction %s of ₹%s. Reason: %s", txRef, amount, reason)
        );

        return updated;
    }
}
