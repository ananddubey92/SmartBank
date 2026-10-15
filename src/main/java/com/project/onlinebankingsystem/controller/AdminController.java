package com.project.onlinebankingsystem.controller;

import com.project.onlinebankingsystem.dto.AccountResponse;
import com.project.onlinebankingsystem.dto.CustomerDetailResponse;
import com.project.onlinebankingsystem.dto.TransactionResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.NotificationType;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import com.project.onlinebankingsystem.service.AccountService;
import com.project.onlinebankingsystem.service.NotificationService;
import com.project.onlinebankingsystem.service.TransactionService;
import com.project.onlinebankingsystem.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final com.project.onlinebankingsystem.service.AuditLogService auditLogService;

    public AdminController(UserService userService,
                           AccountService accountService,
                           TransactionService transactionService,
                           UserRepository userRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           NotificationService notificationService,
                           com.project.onlinebankingsystem.service.AuditLogService auditLogService) {
        this.userService = userService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<com.project.onlinebankingsystem.dto.UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers().stream().map(com.project.onlinebankingsystem.dto.UserResponseDTO::fromEntity).toList());
    }

    @GetMapping("/users/{identifier}")
    public ResponseEntity<CustomerDetailResponse> getCustomerDetails(@PathVariable String identifier) {
        User user = findUser(identifier);
        List<AccountResponse> accounts = accountService.getAccountsByUsername(user.getUsername());

        CustomerDetailResponse response = CustomerDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fatherName(user.getFatherName())
                .motherName(user.getMotherName())
                .dob(user.getDob())
                .pan(user.getMaskedPan())
                .aadhar(user.getMaskedAadhar())
                .maskedPan(user.getMaskedPan())
                .maskedAadhar(user.getMaskedAadhar())
                .gender(user.getGender())
                .address(user.getAddress())
                .role(user.getRole())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lastLoginTime(user.getLastLoginTime())
                .accountSecurityStatus(user.getAccountSecurityStatus())
                .accounts(accounts)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/fraud-dashboard")
    public ResponseEntity<Map<String, Object>> getFraudDashboard() {
        List<TransactionResponse> allTx = transactionService.getAllTransactions();
        List<AccountResponse> accounts = accountService.getAllAccounts();

        long total = allTx.size();
        long blocked = allTx.stream().filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus())).count();
        long underReview = allTx.stream().filter(t -> "UNDER_REVIEW".equalsIgnoreCase(t.getStatus())).count();
        long highOrCritical = allTx.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long safe = allTx.stream().filter(t -> "LOW".equalsIgnoreCase(t.getRiskLevel()) || "MEDIUM".equalsIgnoreCase(t.getRiskLevel())).count();

        BigDecimal totalLiquidity = accounts.stream()
                .map(AccountResponse::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> data = new HashMap<>();
        data.put("totalTransactions", total);
        data.put("fraudDetected", highOrCritical);
        data.put("blockedCount", blocked);
        data.put("underReviewCount", underReview);
        data.put("safeTransactions", safe);
        data.put("totalCustomers", userService.getAllUsers().size());
        data.put("activeAccounts", accounts.size());
        data.put("totalLiquidity", totalLiquidity);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/suspicious-transactions")
    public ResponseEntity<List<TransactionResponse>> getSuspiciousTransactions() {
        List<TransactionResponse> suspicious = transactionService.getAllTransactions().stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel())
                        || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus())
                        || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus()))
                .toList();
        return ResponseEntity.ok(suspicious);
    }

    @PostMapping("/transactions/{identifier}/block")
    public ResponseEntity<TransactionResponse> blockTransaction(@PathVariable String identifier,
                                                                java.security.Principal principal,
                                                                jakarta.servlet.http.HttpServletRequest request) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.BLOCKED);
        tx.setRiskLevel(RiskLevel.CRITICAL);
        tx.setRiskReasons("Manually marked as fraudulent and BLOCKED by System Administrator.");
        Transaction saved = transactionRepository.save(tx);

        String performedBy = (principal != null) ? principal.getName() : "admin";
        String ip = (request != null) ? request.getRemoteAddr() : "127.0.0.1";
        auditLogService.logAction(
                performedBy,
                "ROLE_ADMIN",
                "ADMIN_TRANSACTION_BLOCKED",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.BLOCKED.name(),
                ip,
                "SUCCESS",
                "Admin manually blocked transaction " + saved.getTransactionReference()
        );

        if (tx.getSourceAccount() != null && tx.getSourceAccount().getUser() != null) {
            notificationService.createNotification(tx.getSourceAccount().getUser(), NotificationType.TRANSACTION_BLOCKED,
                    "Transaction Blocked by Admin",
                    String.format("Transaction %s (₹%s) was reviewed and blocked by security operations.",
                            tx.getTransactionReference(), tx.getAmount()),
                    "/security");
        }

        return ResponseEntity.ok(transactionService.convertToResponse(saved));
    }

    @PostMapping("/transactions/{identifier}/review")
    public ResponseEntity<TransactionResponse> reviewTransaction(@PathVariable String identifier,
                                                                 java.security.Principal principal,
                                                                 jakarta.servlet.http.HttpServletRequest request) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.UNDER_REVIEW);
        tx.setRiskReasons("Transaction placed under forensic security review by Admin.");
        Transaction saved = transactionRepository.save(tx);

        String performedBy = (principal != null) ? principal.getName() : "admin";
        String ip = (request != null) ? request.getRemoteAddr() : "127.0.0.1";
        auditLogService.logAction(
                performedBy,
                "ROLE_ADMIN",
                "ADMIN_TRANSACTION_REVIEW",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.UNDER_REVIEW.name(),
                ip,
                "SUCCESS",
                "Admin placed transaction " + saved.getTransactionReference() + " under forensic review"
        );

        return ResponseEntity.ok(transactionService.convertToResponse(saved));
    }

    @PostMapping("/transactions/{identifier}/mark-safe")
    public ResponseEntity<TransactionResponse> markTransactionSafe(@PathVariable String identifier,
                                                                   java.security.Principal principal,
                                                                   jakarta.servlet.http.HttpServletRequest request) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setRiskLevel(RiskLevel.LOW);
        tx.setRiskScore(15);
        tx.setRiskReasons("Cleared as SAFE by Administrator after forensic inspection.");
        Transaction saved = transactionRepository.save(tx);

        String performedBy = (principal != null) ? principal.getName() : "admin";
        String ip = (request != null) ? request.getRemoteAddr() : "127.0.0.1";
        auditLogService.logAction(
                performedBy,
                "ROLE_ADMIN",
                "ADMIN_TRANSACTION_MARKED_SAFE",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.COMPLETED.name(),
                ip,
                "SUCCESS",
                "Admin marked transaction " + saved.getTransactionReference() + " safe"
        );

        if (tx.getSourceAccount() != null && tx.getSourceAccount().getUser() != null) {
            notificationService.createNotification(tx.getSourceAccount().getUser(), NotificationType.TRANSACTION_SUCCESS,
                    "Transaction Cleared by Admin",
                    String.format("Transaction %s (₹%s) was verified safe by security operations.",
                            tx.getTransactionReference(), tx.getAmount()),
                    "/transactions");
        }

        return ResponseEntity.ok(transactionService.convertToResponse(saved));
    }

    @PutMapping("/accounts/{accountNumber}/status")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable String accountNumber,
            @RequestParam String status,
            java.security.Principal principal,
            jakarta.servlet.http.HttpServletRequest request) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        String prevStatus = account.getStatus().name();
        AccountStatus newStatus = AccountStatus.valueOf(status.toUpperCase());
        account.setStatus(newStatus);
        Account saved = accountRepository.save(account);

        String performedBy = (principal != null) ? principal.getName() : "admin";
        String ip = (request != null) ? request.getRemoteAddr() : "127.0.0.1";
        auditLogService.logAction(
                performedBy,
                "ROLE_ADMIN",
                "ADMIN_ACCOUNT_STATUS_CHANGE",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                newStatus.name(),
                ip,
                "SUCCESS",
                "Admin changed account status from " + prevStatus + " to " + newStatus
        );

        return ResponseEntity.ok(accountService.convertToResponse(saved));
    }

    private Transaction findTransaction(String identifier) {
        // Try finding by reference first (handles alphanumeric references like TXN1234ABCD)
        Optional<Transaction> txOpt = transactionRepository.findByTransactionReference(identifier);
        if (txOpt.isPresent()) {
            return txOpt.get();
        }
        // Fallback: try parsing as numeric ID
        try {
            Long id = Long.parseLong(identifier);
            txOpt = transactionRepository.findById(id);
            if (txOpt.isPresent()) {
                return txOpt.get();
            }
        } catch (NumberFormatException ignored) {}

        throw new ResourceNotFoundException("Transaction not found with identifier: " + identifier);
    }

    private User findUser(String identifier) {
        // Fallback: try parsing as numeric ID
        try {
            Long id = Long.parseLong(identifier);
            Optional<User> userById = userRepository.findById(id);
            if (userById.isPresent()) {
                return userById.get();
            }
        } catch (NumberFormatException ignored) {}

        return userRepository.findByUsername(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));
    }
}
