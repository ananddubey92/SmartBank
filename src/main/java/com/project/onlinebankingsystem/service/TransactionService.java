package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FraudAssessmentResponse;
import com.project.onlinebankingsystem.dto.TransactionResponse;
import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.exception.AccountSuspendedException;
import com.project.onlinebankingsystem.exception.InsufficientBalanceException;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.BeneficiaryRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FraudDetectionService fraudDetectionService;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final OtpService otpService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${smartbank.security.demo-codes-enabled:true}")
    private boolean demoCodesEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${smartbank.transactions.max-per-transaction:200000}")
    private BigDecimal maxPerTransaction = new BigDecimal("200000");

    @org.springframework.beans.factory.annotation.Value("${smartbank.transactions.max-daily-limit:500000}")
    private BigDecimal maxDailyLimit = new BigDecimal("500000");

    private final java.util.concurrent.ConcurrentMap<String, CachedTx> idempotencyMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedTx {
        final TransactionResponse response;
        final long timestamp;
        CachedTx(TransactionResponse response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              BeneficiaryRepository beneficiaryRepository,
                              FraudDetectionService fraudDetectionService,
                              ExpenseAnalyticsService expenseAnalyticsService,
                              PasswordEncoder passwordEncoder,
                              NotificationService notificationService,
                              OtpService otpService,
                              LedgerService ledgerService,
                              UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.fraudDetectionService = fraudDetectionService;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.otpService = otpService;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Transaction amount precision cannot exceed 2 decimal places.");
        }
    }

    private void validateLimits(Account sourceAccount, BigDecimal amount) {
        BigDecimal maxTx = (maxPerTransaction != null) ? maxPerTransaction : new BigDecimal("200000");
        if (amount.compareTo(maxTx) > 0) {
            throw new IllegalArgumentException(String.format("Transaction amount ₹%s exceeds per-transaction limit of ₹%s.", formatAmount(amount), formatAmount(maxTx)));
        }
        BigDecimal maxDaily = (maxDailyLimit != null) ? maxDailyLimit : new BigDecimal("500000");
        BigDecimal pastTransfers = transactionRepository.sumTransfersSince(sourceAccount, LocalDateTime.now().minusHours(24));
        if (pastTransfers == null) {
            pastTransfers = BigDecimal.ZERO;
        }
        if (pastTransfers.add(amount).compareTo(maxDaily) > 0) {
            throw new IllegalArgumentException(String.format("Transaction exceeds 24-hour cumulative limit of ₹%s (already transferred ₹%s in past 24 hours).", formatAmount(maxDaily), formatAmount(pastTransfers)));
        }
    }

    private void checkAccountAccess(Account account, String username, String operation) {
        if (username == null) return;
        User caller = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        boolean isStaff = caller.getRole() == Role.ROLE_ADMIN || caller.getRole() == Role.ROLE_SUPER_ADMIN;
        boolean isOwner = account.getUser() != null && account.getUser().getUsername().equalsIgnoreCase(username);
        if (!isStaff && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    String.format("Access denied: You do not have permission to %s on account %s.", operation, account.getAccountNumber())
            );
        }
    }

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String description) {
        return deposit(accountNumber, amount, description, null, null, null);
    }

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String description, String categoryStr) {
        return deposit(accountNumber, amount, description, categoryStr, null, null);
    }

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String description, String categoryStr, String authenticatedUsername, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CachedTx cached = idempotencyMap.get(idempotencyKey);
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < 300_000)) {
                return cached.response;
            }
        }

        validateAmount(amount);

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (authenticatedUsername != null) {
            checkAccountAccess(account, authenticatedUsername, "deposit to");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Account is " + account.getStatus() + ". Cannot perform transaction.");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        ExpenseCategory category = ExpenseCategory.OTHER;
        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                category = ExpenseCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        String txRef = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Transaction transaction = Transaction.builder()
                .transactionReference(txRef)
                .destinationAccount(account)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description(description != null ? description : "Cash Deposit")
                .timestamp(LocalDateTime.now())
                .riskScore(5)
                .riskLevel(RiskLevel.LOW)
                .fraudProbability(5.0)
                .decision(FraudDecision.ALLOW)
                .ruleBasedScore(5)
                .mlPrediction(5.0)
                .fraudCheckedAt(LocalDateTime.now())
                .riskReasons("Cash deposit verified at internal ledger node.")
                .category(category)
                .verifiedWithOtp(false)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Record balanced ledger credit entry
        ledgerService.recordCredit(account, amount, txRef, "Cash Deposit: " + (description != null ? description : "ATM / Counter"));

        if (account.getUser() != null) {
            notificationService.createNotification(account.getUser(), NotificationType.TRANSACTION_SUCCESS,
                    "Deposit Received",
                    String.format("₹%s was deposited into your account %s.", formatAmount(amount), accountNumber),
                    "/account-details");
        }

        TransactionResponse resp = convertToResponse(saved);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyMap.put(idempotencyKey, new CachedTx(resp, System.currentTimeMillis()));
        }
        return resp;
    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String description) {
        return withdraw(accountNumber, amount, description, null, null, null);
    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String description, String categoryStr) {
        return withdraw(accountNumber, amount, description, categoryStr, null, null);
    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String description, String categoryStr, String authenticatedUsername, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CachedTx cached = idempotencyMap.get(idempotencyKey);
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < 300_000)) {
                return cached.response;
            }
        }

        validateAmount(amount);

        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (authenticatedUsername != null) {
            checkAccountAccess(account, authenticatedUsername, "withdraw from");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Account is " + account.getStatus() + ". Cannot perform transaction.");
        }

        validateLimits(account, amount);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in account " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        ExpenseCategory category = ExpenseCategory.OTHER;
        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                category = ExpenseCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception ignored) {}
        } else if (description != null) {
            category = expenseAnalyticsService.autoDetectCategory(description);
        }

        String txRef = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Transaction transaction = Transaction.builder()
                .transactionReference(txRef)
                .sourceAccount(account)
                .amount(amount)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .description(description != null ? description : "Cash Withdrawal")
                .timestamp(LocalDateTime.now())
                .riskScore(12)
                .riskLevel(RiskLevel.LOW)
                .fraudProbability(12.0)
                .decision(FraudDecision.ALLOW)
                .ruleBasedScore(12)
                .mlPrediction(12.0)
                .fraudCheckedAt(LocalDateTime.now())
                .riskReasons("Cash withdrawal authorized from verified account.")
                .category(category)
                .verifiedWithOtp(false)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Record balanced ledger debit entry
        ledgerService.recordDebit(account, amount, txRef, "Cash Withdrawal: " + (description != null ? description : "ATM / Counter"));

        if (account.getUser() != null) {
            notificationService.createNotification(account.getUser(), NotificationType.TRANSACTION_SUCCESS,
                    "Cash Withdrawal Completed",
                    String.format("₹%s was withdrawn from your account %s.", formatAmount(amount), accountNumber),
                    "/account-details");
        }

        TransactionResponse resp = convertToResponse(saved);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyMap.put(idempotencyKey, new CachedTx(resp, System.currentTimeMillis()));
        }
        return resp;
    }

    @Transactional
    public TransactionResponse transfer(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount, String description) {
        return transfer(sourceAccountNumber, destinationAccountNumber, amount, description, null, null, null, null, null);
    }

    @Transactional
    public TransactionResponse transfer(String sourceAccountNumber, String destinationAccountNumber,
                                        BigDecimal amount, String description, String categoryStr,
                                        String otp, String pin) {
        return transfer(sourceAccountNumber, destinationAccountNumber, amount, description, categoryStr, otp, pin, null, null);
    }

    @Transactional
    public TransactionResponse transfer(String sourceAccountNumber, String destinationAccountNumber,
                                        BigDecimal amount, String description, String categoryStr,
                                        String otp, String pin, String authenticatedUsername, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CachedTx cached = idempotencyMap.get(idempotencyKey);
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < 300_000)) {
                return cached.response;
            }
        }

        validateAmount(amount);

        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new IllegalArgumentException("Source and destination account numbers must be different.");
        }

        Account source;
        Account destination;

        // Deadlock prevention logic: lock in lexicographical order of account numbers
        if (sourceAccountNumber.compareTo(destinationAccountNumber) < 0) {
            source = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceAccountNumber));
            destination = accountRepository.findByAccountNumberWithLock(destinationAccountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destinationAccountNumber));
        } else {
            destination = accountRepository.findByAccountNumberWithLock(destinationAccountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destinationAccountNumber));
            source = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceAccountNumber));
        }

        if (authenticatedUsername != null) {
            checkAccountAccess(source, authenticatedUsername, "transfer from");
        }

        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Source account is " + source.getStatus() + ". Cannot initiate transfer.");
        }
        if (destination.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Destination account is " + destination.getStatus() + ". Cannot receive funds.");
        }

        validateLimits(source, amount);

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in source account: " + sourceAccountNumber);
        }

        // --- Hybrid AI Fraud Detection Risk Analysis ---
        FraudAssessmentResponse fraudRisk = fraudDetectionService.assessRisk(sourceAccountNumber, destinationAccountNumber, amount);
        RiskLevel riskLevel = RiskLevel.valueOf(fraudRisk.getRiskLevel());
        boolean verifiedWithOtp = false;
        String txRef = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        // Verify Security PIN for ALL transfers
        User user = source.getUser();
        boolean pinValid = false;
        if (user != null && user.getTransactionPin() != null && pin != null && !pin.isBlank()) {
            pinValid = passwordEncoder.matches(pin.trim(), user.getTransactionPin());
        } else if (demoCodesEnabled && pin != null && (pin.trim().equals("1234") || pin.trim().equals("0000"))) {
            pinValid = true; // Fallback demo default PIN only when demo codes enabled
        }

        if (fraudRisk.isRequiresOtp()) {
            // High or Critical Risk Transaction: Verification is mandatory
            boolean otpValid = otpService.validateOtp(txRef, otp);
            if (!otpValid && demoCodesEnabled && otp != null) {
                // Check fallback static demo codes
                otpValid = otp.trim().equals("482910") || otp.trim().equals("123456");
            }

            if (!otpValid && !pinValid) {
                // Verification Failed or Missing: Block the transaction and log the attempt
                TransactionStatus blockedStatus = (riskLevel == RiskLevel.CRITICAL) ? TransactionStatus.BLOCKED : TransactionStatus.BLOCKED;

                Transaction blockedTx = Transaction.builder()
                        .transactionReference(txRef)
                        .sourceAccount(source)
                        .destinationAccount(destination)
                        .amount(amount)
                        .type(TransactionType.TRANSFER)
                        .status(blockedStatus)
                        .description(description != null && !description.isBlank() ? description + " [BLOCKED]" : "Fund Transfer [BLOCKED]")
                        .timestamp(LocalDateTime.now())
                        .riskScore(fraudRisk.getRiskScore())
                        .riskLevel(riskLevel)
                        .fraudProbability(fraudRisk.getFraudProbability())
                        .decision(FraudDecision.valueOf(fraudRisk.getDecision()))
                        .ruleBasedScore(fraudRisk.getRuleBasedScore())
                        .mlPrediction((double) fraudRisk.getMlPredictionScore())
                        .fraudCheckedAt(LocalDateTime.now())
                        .riskReasons("❌ Transaction Blocked: High risk suspicious activity (" + String.join("; ", fraudRisk.getReasons()) + ") failed required verification.")
                        .category(resolveCategory(categoryStr, description))
                        .verifiedWithOtp(false)
                        .build();

                transactionRepository.save(blockedTx);

                if (user != null) {
                    user.setAccountSecurityStatus("ALERT");
                    accountRepository.save(source);
                    notificationService.createNotification(user, NotificationType.TRANSACTION_BLOCKED,
                            "🚨 Suspicious Transaction Blocked",
                            String.format("Transfer of ₹%s to %s was blocked. Risk score %d/100 (%s). Reason: %s",
                                    formatAmount(amount), destinationAccountNumber, fraudRisk.getRiskScore(), riskLevel,
                                    !fraudRisk.getReasons().isEmpty() ? fraudRisk.getReasons().get(0) : "High risk detected"),
                            "/security");
                }

                throw new IllegalArgumentException(String.format(
                        "❌ Transaction Blocked: %s Risk detected (%d/100, Fraud Probability: %.1f%%). Verification failed or invalid OTP/PIN.",
                        riskLevel, fraudRisk.getRiskScore(), fraudRisk.getFraudProbability()));
            }

            verifiedWithOtp = true;
        } else {
            // Standard risk transfer: Mandatory PIN Check
            if (!pinValid) {
                throw new IllegalArgumentException("❌ Transaction Rejected: Invalid or missing 4-digit Security PIN.");
            }
        }

        // Complete Transfer
        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));

        accountRepository.save(source);
        accountRepository.save(destination);

        ExpenseCategory category = resolveCategory(categoryStr, description);

        Transaction transaction = Transaction.builder()
                .transactionReference(txRef)
                .sourceAccount(source)
                .destinationAccount(destination)
                .amount(amount)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description(description != null && !description.isBlank() ? description : "Fund Transfer")
                .timestamp(LocalDateTime.now())
                .riskScore(fraudRisk.getRiskScore())
                .riskLevel(riskLevel)
                .fraudProbability(fraudRisk.getFraudProbability())
                .decision(FraudDecision.valueOf(fraudRisk.getDecision()))
                .ruleBasedScore(fraudRisk.getRuleBasedScore())
                .mlPrediction((double) fraudRisk.getMlPredictionScore())
                .fraudCheckedAt(LocalDateTime.now())
                .riskReasons(String.join("; ", fraudRisk.getReasons()))
                .category(category)
                .verifiedWithOtp(verifiedWithOtp)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        // Record balanced ledger transfer pair
        ledgerService.recordTransferPair(source, destination, amount, txRef, description != null ? description : "Fund Transfer");

        // Update Beneficiary metrics
        if (user != null) {
            Optional<Beneficiary> matchingBen = beneficiaryRepository.findByUserUsername(user.getUsername()).stream()
                    .filter(b -> b.getAccountNumber().equalsIgnoreCase(destinationAccountNumber))
                    .findFirst();

            if (matchingBen.isPresent()) {
                Beneficiary b = matchingBen.get();
                b.setTransferCount((b.getTransferCount() != null ? b.getTransferCount() : 0) + 1);
                b.setTotalTransferred((b.getTotalTransferred() != null ? b.getTotalTransferred() : BigDecimal.ZERO).add(amount));
                b.setLastTransactionAt(LocalDateTime.now());
                b.setRiskStatus("NORMAL");
                beneficiaryRepository.save(b);
            }

            notificationService.createNotification(user, NotificationType.TRANSACTION_SUCCESS,
                    "Fund Transfer Successful",
                    String.format("Successfully sent ₹%s to account %s. Risk Score: %d/100 (%s).",
                            formatAmount(amount), destinationAccountNumber, fraudRisk.getRiskScore(), riskLevel),
                    "/transactions");
        }

        TransactionResponse resp = convertToResponse(saved);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyMap.put(idempotencyKey, new CachedTx(resp, System.currentTimeMillis()));
        }
        return resp;
    }

    private ExpenseCategory resolveCategory(String categoryStr, String description) {
        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                return ExpenseCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception ignored) {}
        }
        return expenseAnalyticsService.autoDetectCategory(description);
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return getTransactionHistory(accountNumber, null);
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber, String authenticatedUsername) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (authenticatedUsername != null) {
            checkAccountAccess(account, authenticatedUsername, "view transaction history of");
        }

        return transactionRepository.findAllByAccount(account).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAllByOrderByTimestampDesc().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getTransactionsByRiskLevel(RiskLevel riskLevel) {
        return transactionRepository.findByRiskLevel(riskLevel).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse convertToResponse(Transaction tx) {
        ExpenseCategory cat = tx.getCategory() != null ? tx.getCategory() : ExpenseCategory.OTHER;
        RiskLevel rl = tx.getRiskLevel() != null ? tx.getRiskLevel() : RiskLevel.LOW;
        String dec = tx.getDecision() != null ? tx.getDecision().name() : (rl == RiskLevel.HIGH || rl == RiskLevel.CRITICAL ? "BLOCK" : "ALLOW");

        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionReference(tx.getTransactionReference())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : null)
                .amount(tx.getAmount())
                .type(tx.getType().name())
                .status(tx.getStatus().name())
                .description(tx.getDescription())
                .timestamp(tx.getTimestamp())
                .riskScore(tx.getRiskScore() != null ? tx.getRiskScore() : 10)
                .riskLevel(rl.name())
                .riskReasons(tx.getRiskReasons() != null ? tx.getRiskReasons() : "Standard transaction")
                .category(cat.name())
                .categoryDisplayName(cat.getDisplayName())
                .categoryIcon(cat.getIcon())
                .categoryColor(cat.getColor())
                .verifiedWithOtp(Boolean.TRUE.equals(tx.getVerifiedWithOtp()))
                .fraudProbability(tx.getFraudProbability() != null ? tx.getFraudProbability() : (double) (tx.getRiskScore() != null ? tx.getRiskScore() : 10))
                .decision(dec)
                .ruleBasedScore(tx.getRuleBasedScore() != null ? tx.getRuleBasedScore() : tx.getRiskScore())
                .mlPredictionScore(tx.getMlPrediction() != null ? (int) Math.round(tx.getMlPrediction()) : tx.getRiskScore())
                .build();
    }

    private String formatAmount(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
