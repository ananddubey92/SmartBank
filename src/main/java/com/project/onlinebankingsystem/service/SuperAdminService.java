package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.AuditLog;
import com.project.onlinebankingsystem.model.DebitCard;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class SuperAdminService {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminService.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final com.project.onlinebankingsystem.repository.DebitCardRepository debitCardRepository;
    private final DebitCardService debitCardService;

    public SuperAdminService(UserRepository userRepository,
                             AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             AccountService accountService,
                             TransactionService transactionService,
                             AuditLogService auditLogService,
                             PasswordEncoder passwordEncoder,
                             com.project.onlinebankingsystem.repository.DebitCardRepository debitCardRepository,
                             DebitCardService debitCardService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.debitCardRepository = debitCardRepository;
        this.debitCardService = debitCardService;
    }

    public SuperAdminDashboardDTO getDashboardMetrics() {
        long totalCustomers = userRepository.countByRole(Role.ROLE_CUSTOMER);
        List<User> admins = userRepository.findByRole(Role.ROLE_ADMIN);
        long totalAdmins = admins.size();
        long activeAdmins = admins.stream().filter(a -> (a.getEnabled() == null || a.getEnabled()) && !"SUSPENDED".equalsIgnoreCase(a.getAccountSecurityStatus()) && !"LOCKED".equalsIgnoreCase(a.getAccountSecurityStatus())).count();
        long inactiveAdmins = totalAdmins - activeAdmins;

        List<Account> accounts = accountRepository.findAll();
        long totalAccounts = accounts.size();
        long activeAccounts = accounts.stream().filter(a -> a.getStatus() == AccountStatus.ACTIVE).count();
        long frozenAccounts = accounts.stream().filter(a -> a.getStatus() == AccountStatus.SUSPENDED || a.getStatus() == AccountStatus.FROZEN).count();
        long blockedAccounts = accounts.stream().filter(a -> a.getStatus() == AccountStatus.CLOSED || a.getStatus() == AccountStatus.SUSPENDED || a.getStatus() == AccountStatus.FROZEN).count();

        BigDecimal totalLiquidity = accounts.stream()
                .map(Account::getBalance)
                .filter(b -> b != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TransactionResponse> allTx = transactionService.getAllTransactions();
        long totalTx = allTx.size();
        long successfulTx = allTx.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()) || "SUCCESS".equalsIgnoreCase(t.getStatus())).count();
        long failedTx = allTx.stream().filter(t -> "FAILED".equalsIgnoreCase(t.getStatus()) || "BLOCKED".equalsIgnoreCase(t.getStatus())).count();
        long pendingTx = allTx.stream().filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus())).count();
        long suspiciousTx = allTx.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel()) || "BLOCKED".equalsIgnoreCase(t.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus())).count();
        long highRiskTx = allTx.stream().filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())).count();
        long blockedTx = allTx.stream().filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus())).count();
        long pendingReviews = allTx.stream().filter(t -> "UNDER_REVIEW".equalsIgnoreCase(t.getStatus())).count();

        List<User> allUsers = userRepository.findAll();
        long activeUsers = allUsers.stream().filter(u -> (u.getEnabled() == null || u.getEnabled()) && !"SUSPENDED".equalsIgnoreCase(u.getAccountSecurityStatus()) && !"LOCKED".equalsIgnoreCase(u.getAccountSecurityStatus())).count();

        BigDecimal totalDeposits = allTx.stream()
                .filter(t -> "DEPOSIT".equalsIgnoreCase(t.getType()) || "CREDIT".equalsIgnoreCase(t.getType()))
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWithdrawals = allTx.stream()
                .filter(t -> "WITHDRAWAL".equalsIgnoreCase(t.getType()) || "DEBIT".equalsIgnoreCase(t.getType()))
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TransactionResponse> recentTx = allTx.stream()
                .sorted(Comparator.comparing(TransactionResponse::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        List<SuperAdminDashboardDTO.UserSummaryDTO> recentUsers = userRepository.findByRole(Role.ROLE_CUSTOMER).stream()
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(u -> SuperAdminDashboardDTO.UserSummaryDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .phone(u.getPhone())
                        .accountSecurityStatus(u.getAccountSecurityStatus())
                        .role(u.getRole() != null ? u.getRole().name() : null)
                        .enabled(u.getEnabled() != null ? u.getEnabled() : true)
                        .build())
                .toList();

        List<AuditLog> recentAudits = auditLogService.getRecentAuditLogs().stream()
                .limit(10)
                .toList();

        long totalDebitCards = debitCardRepository.count();
        long activeDebitCards = debitCardRepository.countByStatus(CardStatus.ACTIVE);
        long blockedDebitCards = debitCardRepository.countByStatus(CardStatus.BLOCKED);
        long missingDebitCards = Math.max(0, activeAccounts - totalDebitCards);

        long pendingKyc = userRepository.countByKycStatus(KycStatus.PENDING);
        long pendingApprovals = accountRepository.countByStatus(AccountStatus.PENDING);
        List<AccountResponse> recentAccounts = accountRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::enrichAccountResponse)
                .toList();

        return SuperAdminDashboardDTO.builder()
                .totalCustomers(totalCustomers)
                .totalAdmins(totalAdmins)
                .activeAdmins(activeAdmins)
                .inactiveAdmins(inactiveAdmins)
                .totalAccounts(totalAccounts)
                .activeAccounts(activeAccounts)
                .frozenAccounts(frozenAccounts)
                .blockedAccounts(blockedAccounts)
                .totalDebitCards(totalDebitCards)
                .activeDebitCards(activeDebitCards)
                .blockedDebitCards(blockedDebitCards)
                .missingDebitCards(missingDebitCards)
                .pendingKyc(pendingKyc)
                .pendingApprovals(pendingApprovals)
                .recentAccountOpenings(recentAccounts)
                .totalTransactions(totalTx)
                .successfulTransactions(successfulTx)
                .failedTransactions(failedTx)
                .pendingTransactions(pendingTx)
                .suspiciousTransactions(suspiciousTx)
                .highRiskTransactions(highRiskTx)
                .blockedTransactions(blockedTx)
                .pendingReviews(pendingReviews)
                .activeUsers(activeUsers)
                .totalLiquidity(totalLiquidity)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalBankBalance(totalLiquidity)
                .recentTransactions(recentTx)
                .recentRegistrations(recentUsers)
                .recentAuditLogs(recentAudits)
                .build();
    }

    // ================= ADMIN MANAGEMENT =================
    public List<UserResponseDTO> getAllAdmins() {
        return userRepository.findByRole(Role.ROLE_ADMIN).stream()
                .map(UserResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public UserResponseDTO createAdmin(CreateAdminRequest request, String performedBy, String ipAddress) {
        String username = request.getUsername().trim().toLowerCase();
        if ("superadmin".equalsIgnoreCase(username) || "admin".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is reserved and cannot be created.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken: " + username);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Phone number is already registered: " + request.getPhone());
        }

        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .pan(request.getPan() != null && !request.getPan().isBlank() ? request.getPan() : null)
                .aadhar(request.getAadhar() != null && !request.getAadhar().isBlank() ? request.getAadhar() : null)
                .address(request.getAddress() != null && !request.getAddress().isBlank() ? request.getAddress() : "Bank Operations Office")
                .gender(Gender.OTHERS)
                .role(Role.ROLE_ADMIN)
                .accountSecurityStatus("GOOD")
                .failedLoginAttempts(0)
                .enabled(true)
                .build();

        User saved = userRepository.save(admin);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "SUPER_ADMIN_CREATED_ADMIN",
                "USER",
                saved.getUsername(),
                "NONE",
                "ROLE_ADMIN",
                ipAddress,
                "SUCCESS",
                "Super Admin created new operational Admin: " + saved.getUsername()
        );

        return UserResponseDTO.fromEntity(saved);
    }

    @Transactional
    public UserResponseDTO updateAdminStatus(String identifier, String newStatus, String performedBy, String ipAddress) {
        User user = findUser(identifier);
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException("Super Admin status cannot be altered.");
        }
        if (user.getRole() != Role.ROLE_ADMIN) {
            throw new IllegalArgumentException("Target user is not an Admin.");
        }

        String prevStatus = user.getAccountSecurityStatus();
        boolean enableFlag = !"SUSPENDED".equalsIgnoreCase(newStatus) && !"LOCKED".equalsIgnoreCase(newStatus) && !"DISABLED".equalsIgnoreCase(newStatus);

        user.setAccountSecurityStatus(newStatus.toUpperCase());
        user.setEnabled(enableFlag);
        if (enableFlag) {
            user.setFailedLoginAttempts(0);
        }
        User saved = userRepository.save(user);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ADMIN_STATUS_CHANGED",
                "USER",
                saved.getUsername(),
                prevStatus,
                newStatus.toUpperCase(),
                ipAddress,
                "SUCCESS",
                "Super Admin changed Admin status to " + newStatus.toUpperCase()
        );

        return UserResponseDTO.fromEntity(saved);
    }

    // ================= CUSTOMER MANAGEMENT =================
    public List<UserResponseDTO> getAllCustomers() {
        return userRepository.findByRole(Role.ROLE_CUSTOMER).stream()
                .map(UserResponseDTO::fromEntity)
                .toList();
    }

    public CustomerDetailResponse getCustomerDetails(String identifier) {
        User user = findUser(identifier);
        List<AccountResponse> accounts = accountService.getAccountsByUsername(user.getUsername());

        return CustomerDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fatherName(user.getFatherName())
                .motherName(user.getMotherName())
                .dob(user.getDob())
                .pan(user.getPan())
                .aadhar(user.getAadhar())
                .gender(user.getGender())
                .address(user.getAddress())
                .role(user.getRole())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lastLoginTime(user.getLastLoginTime())
                .accountSecurityStatus(user.getAccountSecurityStatus())
                .enabled(user.getEnabled() != null ? user.getEnabled() : true)
                .accounts(accounts)
                .build();
    }

    @Transactional
    public UserResponseDTO updateCustomerStatus(String identifier, String newStatus, String performedBy, String ipAddress) {
        User user = findUser(identifier);
        if (user.getRole() != Role.ROLE_CUSTOMER) {
            throw new IllegalArgumentException("Target user is not a Customer.");
        }

        String prevStatus = user.getAccountSecurityStatus();
        boolean enableFlag = !"SUSPENDED".equalsIgnoreCase(newStatus) && !"LOCKED".equalsIgnoreCase(newStatus) && !"DISABLED".equalsIgnoreCase(newStatus);

        user.setAccountSecurityStatus(newStatus.toUpperCase());
        user.setEnabled(enableFlag);
        if (enableFlag) {
            user.setFailedLoginAttempts(0);
        }
        User saved = userRepository.save(user);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "CUSTOMER_STATUS_CHANGED",
                "USER",
                saved.getUsername(),
                prevStatus,
                newStatus.toUpperCase(),
                ipAddress,
                "SUCCESS",
                "Super Admin changed Customer status to " + newStatus.toUpperCase()
        );

        return UserResponseDTO.fromEntity(saved);
    }

    // ================= ACCOUNT MANAGEMENT =================
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::enrichAccountResponse)
                .toList();
    }

    public AccountResponse getAccountDetails(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return enrichAccountResponse(account);
    }

    @Transactional
    public AccountResponse createAccount(SuperAdminCreateAccountRequest request, String performedBy, String ipAddress) {
        if (request.getCustomerUsername() == null || request.getCustomerUsername().isBlank()) {
            throw new IllegalArgumentException("Customer username is required.");
        }
        User customer = userRepository.findByUsername(request.getCustomerUsername().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with username: " + request.getCustomerUsername()));

        AccountType accountType;
        try {
            accountType = AccountType.valueOf(request.getAccountType().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid account type: " + request.getAccountType() + ". Valid types are SAVING, CURRENT, FD, RD.");
        }

        BigDecimal balance = request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative.");
        }

        String accountNumber;
        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            accountNumber = request.getAccountNumber().trim();
            if (!accountNumber.matches("\\d{10,16}")) {
                throw new IllegalArgumentException("Account number must contain between 10 and 16 digits.");
            }
            if (accountRepository.existsByAccountNumber(accountNumber)) {
                throw new IllegalArgumentException("Account number already exists: " + accountNumber);
            }
        } else {
            accountNumber = generateUniqueAccountNumber();
        }

        String ifsc = (request.getIfscCode() != null && !request.getIfscCode().isBlank())
                ? request.getIfscCode().trim().toUpperCase() : "OBIN0000101";

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ifscCode(ifsc)
                .accountType(accountType)
                .balance(balance)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .accountHolderName(customer.getFullName())
                .user(customer)
                .build();

        Account saved = accountRepository.save(account);

        // Auto-generate Debit Card for newly created account
        try {
            debitCardService.generateDebitCardForAccount(saved);
        } catch (Exception e) {
            // Log warning to allow account creation if card generation encountered non-fatal edge case
        }

        // Record initial ledger deposit transaction if balance > 0
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            Transaction initialTx = Transaction.builder()
                    .transactionReference("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                    .destinationAccount(saved)
                    .amount(balance)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.COMPLETED)
                    .description("Initial Opening Balance Deposit by Super Admin")
                    .timestamp(LocalDateTime.now())
                    .riskScore(0)
                    .riskLevel(RiskLevel.LOW)
                    .fraudProbability(0.0)
                    .decision(FraudDecision.ALLOW)
                    .ruleBasedScore(0)
                    .mlPrediction(0.0)
                    .fraudCheckedAt(LocalDateTime.now())
                    .riskReasons("Initial opening balance funded by Super Admin.")
                    .category(ExpenseCategory.OTHER)
                    .verifiedWithOtp(false)
                    .build();
            transactionRepository.save(initialTx);
        }

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_CREATED",
                "ACCOUNT",
                saved.getAccountNumber(),
                "NONE",
                AccountStatus.ACTIVE.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin created %s account #%s for customer %s (%s) with initial balance ₹%s",
                        saved.getAccountType(), saved.getAccountNumber(), customer.getUsername(), customer.getFullName(), balance)
        );

        return enrichAccountResponse(saved);
    }

    /**
     * Complete, professional Super Admin bank account opening.
     * Supports both new customer creation with auto-generated credentials,
     * and opening additional accounts for existing verified customers.
     * Automatically issues a unique RuPay Platinum Debit Card when account is ACTIVE.
     */
    @Transactional
    public SuperAdminAccountOpeningResponse openNewAccount(SuperAdminOpenAccountRequest request, String performedBy, String ipAddress) {
        if (request == null) {
            throw new IllegalArgumentException("Account opening request cannot be null.");
        }

        User customer;
        boolean isNewCustomer = false;
        String temporaryPassword = null;

        if (Boolean.TRUE.equals(request.getIsExistingCustomer())) {
            // Existing Customer Mode
            String existingUsername = request.getExistingCustomerUsername();
            if (existingUsername == null || existingUsername.isBlank()) {
                throw new IllegalArgumentException("Please select an existing customer.");
            }
            customer = userRepository.findByUsername(existingUsername.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + existingUsername));
        } else {
            // New Customer Mode
            isNewCustomer = true;
            String fullName = request.getFullName() != null ? request.getFullName().trim() : "";
            if (fullName.isBlank()) {
                throw new IllegalArgumentException("Full Name is required.");
            }

            String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
            if (email.isBlank() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                throw new IllegalArgumentException("A valid Email address is required.");
            }
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email address '" + email + "' is already registered.");
            }

            String phone = request.getPhone() != null ? request.getPhone().trim().replaceAll("[^0-9]", "") : "";
            if (phone.length() != 10) {
                throw new IllegalArgumentException("Mobile number must be exactly 10 digits.");
            }
            if (userRepository.existsByPhone(phone)) {
                throw new IllegalArgumentException("Mobile number '" + phone + "' is already registered.");
            }

            String pan = request.getPan() != null ? request.getPan().trim().toUpperCase() : "";
            if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")) {
                throw new IllegalArgumentException("PAN number must follow standard format (e.g., ABCDE1234F).");
            }
            if (userRepository.existsByPan(pan)) {
                throw new IllegalArgumentException("PAN number '" + pan + "' is already registered with another customer.");
            }

            String aadhar = request.getAadhar() != null ? request.getAadhar().trim().replaceAll("[^0-9]", "") : "";
            if (aadhar.length() != 12) {
                throw new IllegalArgumentException("Aadhaar number must be exactly 12 digits.");
            }
            if (userRepository.existsByAadhar(aadhar)) {
                throw new IllegalArgumentException("Aadhaar number '" + aadhar + "' is already registered with another customer.");
            }

            // Generate clean unique username
            String baseUsername = fullName.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (baseUsername.length() < 3) {
                baseUsername = "user" + (100 + new Random().nextInt(900));
            }
            String username = baseUsername;
            int suffix = 1;
            while (userRepository.existsByUsername(username)) {
                username = baseUsername + (100 + new Random().nextInt(900));
                suffix++;
                if (suffix > 50) {
                    username = baseUsername + (System.currentTimeMillis() % 10000);
                    break;
                }
            }

            // Generate secure temporary password with high entropy
            String secureRandPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            temporaryPassword = "SB@" + Character.toUpperCase(secureRandPart.charAt(0)) + secureRandPart.substring(1) + "!9";

            // Parse DOB
            LocalDate dob = null;
            if (request.getDob() != null && !request.getDob().isBlank()) {
                try {
                    dob = LocalDate.parse(request.getDob().trim());
                } catch (Exception ignored) {}
            }

            // Parse Gender
            Gender gender = null;
            if (request.getGender() != null && !request.getGender().isBlank()) {
                try {
                    gender = Gender.valueOf(request.getGender().trim().toUpperCase());
                } catch (Exception ignored) {}
            }

            // Parse KYC Status
            KycStatus kycStatus = KycStatus.VERIFIED;
            if (request.getKycStatus() != null && !request.getKycStatus().isBlank()) {
                try {
                    kycStatus = KycStatus.valueOf(request.getKycStatus().trim().toUpperCase());
                } catch (Exception ignored) {}
            }

            User newUser = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(temporaryPassword))
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .fatherName(request.getFatherName() != null ? request.getFatherName().trim() : null)
                    .motherName(request.getMotherName() != null ? request.getMotherName().trim() : null)
                    .dob(dob)
                    .gender(gender)
                    .address(request.getAddress() != null ? request.getAddress().trim() : null)
                    .pinCode(request.getPinCode() != null ? request.getPinCode().trim() : null)
                    .pan(pan)
                    .aadhar(aadhar)
                    .kycStatus(kycStatus)
                    .role(Role.ROLE_CUSTOMER)
                    .enabled(true)
                    .failedLoginAttempts(0)
                    .accountSecurityStatus("GOOD")
                    .build();

            customer = userRepository.save(newUser);
        }

        // Parse Account Type
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(request.getAccountType().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid account type: " + request.getAccountType() + ". Valid types are SAVING, CURRENT, FD, RD.");
        }

        // Initial deposit
        BigDecimal balance = request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial deposit cannot be negative.");
        }

        // Account Number
        String accountNumber;
        if (request.getAccountNumber() != null && !request.getAccountNumber().isBlank()) {
            accountNumber = request.getAccountNumber().trim();
            if (!accountNumber.matches("\\d{10,16}")) {
                throw new IllegalArgumentException("Account number must contain between 10 and 16 digits.");
            }
            if (accountRepository.existsByAccountNumber(accountNumber)) {
                throw new IllegalArgumentException("Account number already exists: " + accountNumber);
            }
        } else {
            accountNumber = generateUniqueAccountNumber();
        }

        // Account Status
        AccountStatus accountStatus = AccountStatus.ACTIVE;
        if (request.getAccountStatus() != null && !request.getAccountStatus().isBlank()) {
            try {
                accountStatus = AccountStatus.valueOf(request.getAccountStatus().trim().toUpperCase());
            } catch (Exception e) {
                accountStatus = AccountStatus.ACTIVE;
            }
        }

        String ifsc = (request.getIfscCode() != null && !request.getIfscCode().isBlank())
                ? request.getIfscCode().trim().toUpperCase() : "OBIN0000101";

        String branch = (request.getBranchName() != null && !request.getBranchName().isBlank())
                ? request.getBranchName().trim() : "Main Branch, Mumbai";

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ifscCode(ifsc)
                .branchName(branch)
                .accountType(accountType)
                .balance(balance)
                .status(accountStatus)
                .nomineeName(request.getNomineeName() != null ? request.getNomineeName().trim() : null)
                .nomineeRelationship(request.getNomineeRelationship() != null ? request.getNomineeRelationship().trim() : null)
                .createdAt(LocalDateTime.now())
                .accountHolderName(customer.getFullName())
                .user(customer)
                .build();

        Account saved = accountRepository.save(account);

        // Record initial ledger deposit transaction if balance > 0
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            Transaction initialTx = Transaction.builder()
                    .transactionReference("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                    .destinationAccount(saved)
                    .amount(balance)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.COMPLETED)
                    .description("Initial Opening Balance Deposit by Super Admin")
                    .timestamp(LocalDateTime.now())
                    .riskScore(0)
                    .riskLevel(RiskLevel.LOW)
                    .fraudProbability(0.0)
                    .decision(FraudDecision.ALLOW)
                    .ruleBasedScore(0)
                    .mlPrediction(0.0)
                    .fraudCheckedAt(LocalDateTime.now())
                    .riskReasons("Initial opening balance funded by Super Admin.")
                    .category(ExpenseCategory.OTHER)
                    .verifiedWithOtp(false)
                    .build();
            transactionRepository.save(initialTx);
        }

        // Auto-generate Debit Card if status is ACTIVE
        DebitCard issuedCard = null;
        String cardGenWarning = null;
        if (saved.getStatus() == AccountStatus.ACTIVE) {
            try {
                issuedCard = debitCardService.generateDebitCardForAccount(saved);
            } catch (Exception e) {
                log.warn("Automatic debit card provisioning encountered an issue during account opening for #{}: {}", saved.getAccountNumber(), e.getMessage());
                cardGenWarning = e.getMessage();
            }
        }

        // Mask PAN & Aadhaar
        String maskedPan = maskValue(customer.getPan(), 4);
        String maskedAadhaar = maskAadhaar(customer.getAadhar());

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_OPENED",
                "ACCOUNT",
                saved.getAccountNumber(),
                "NONE",
                saved.getStatus().name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin opened %s account #%s for %s (%s) with deposit ₹%s [Card: %s]",
                        saved.getAccountType(), saved.getAccountNumber(), customer.getUsername(), customer.getFullName(),
                        balance, issuedCard != null ? issuedCard.getMaskedCardNumber() : "None")
        );

        return SuperAdminAccountOpeningResponse.builder()
                .accountNumber(saved.getAccountNumber())
                .accountType(saved.getAccountType().name())
                .branchName(saved.getBranchName())
                .ifscCode(saved.getIfscCode())
                .balance(saved.getBalance())
                .accountStatus(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .customerUsername(customer.getUsername())
                .customerFullName(customer.getFullName())
                .customerEmail(customer.getEmail())
                .customerPhone(customer.getPhone())
                .maskedPan(maskedPan)
                .maskedAadhaar(maskedAadhaar)
                .kycStatus(customer.getKycStatus() != null ? customer.getKycStatus().name() : "VERIFIED")
                .temporaryPassword(temporaryPassword)
                .isNewCustomer(isNewCustomer)
                .hasDebitCard(issuedCard != null)
                .debitCardNumber(issuedCard != null ? issuedCard.getMaskedCardNumber() : null)
                .debitCardType(issuedCard != null && issuedCard.getCardType() != null ? issuedCard.getCardType().name() : "RUPAY_PLATINUM")
                .debitCardStatus(issuedCard != null && issuedCard.getStatus() != null ? issuedCard.getStatus().name() : "ACTIVE")
                .debitCardExpiry(issuedCard != null && issuedCard.getExpiryDate() != null ? issuedCard.getExpiryDate().toString() : null)
                .message(cardGenWarning == null ? "Bank Account #" + saved.getAccountNumber() + " successfully opened and provisioned."
                        : "Bank Account #" + saved.getAccountNumber() + " opened, but card generation deferred: " + cardGenWarning)
                .build();
    }

    @Transactional
    public AccountResponse approveAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        // Provision debit card if missing
        try {
            debitCardService.generateDebitCardForAccount(saved);
        } catch (Exception ignored) {}

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_APPROVED",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.ACTIVE.name(),
                ipAddress,
                "SUCCESS",
                "Super Admin approved and activated pending account #" + accountNumber
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse rejectAccount(String accountNumber, String reason, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.CLOSED);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_REJECTED",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.CLOSED.name(),
                ipAddress,
                "SUCCESS",
                "Super Admin rejected account #" + accountNumber + ". Reason: " + (reason != null ? reason : "Administrative decision")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public DebitCardResponse issueDebitCardForAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        DebitCard card = debitCardService.generateDebitCardForAccount(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "DEBIT_CARD_ISSUED",
                "DEBIT_CARD",
                card.getMaskedCardNumber(),
                "NONE",
                card.getStatus().name(),
                ipAddress,
                "SUCCESS",
                "Super Admin issued missing debit card for account #" + accountNumber
        );

        return debitCardService.convertToResponse(card);
    }

    private String maskValue(String val, int visibleEnd) {
        if (val == null || val.isBlank()) return "N/A";
        int len = val.length();
        if (len <= visibleEnd) return val;
        return "X".repeat(len - visibleEnd) + val.substring(len - visibleEnd);
    }

    private String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.isBlank()) return "N/A";
        String clean = aadhaar.replaceAll("[^0-9]", "");
        if (clean.length() == 12) {
            return "XXXX-XXXX-" + clean.substring(8);
        }
        return maskValue(aadhaar, 4);
    }

    @Transactional
    public AccountResponse updateAccount(String accountNumber, SuperAdminUpdateAccountRequest request, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevDetails = String.format("type=%s, status=%s, holder=%s, ifsc=%s",
                account.getAccountType(), account.getStatus(), account.getAccountHolderName(), account.getIfscCode());

        if (request.getAccountType() != null && !request.getAccountType().isBlank()) {
            try {
                account.setAccountType(AccountType.valueOf(request.getAccountType().trim().toUpperCase()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid account type: " + request.getAccountType());
            }
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                account.setStatus(AccountStatus.valueOf(request.getStatus().trim().toUpperCase()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid account status: " + request.getStatus());
            }
        }

        if (request.getAccountHolderName() != null && !request.getAccountHolderName().isBlank()) {
            account.setAccountHolderName(request.getAccountHolderName().trim());
        }

        if (request.getIfscCode() != null && !request.getIfscCode().isBlank()) {
            account.setIfscCode(request.getIfscCode().trim().toUpperCase());
        }

        Account saved = accountRepository.save(account);

        String newDetails = String.format("type=%s, status=%s, holder=%s, ifsc=%s",
                saved.getAccountType(), saved.getStatus(), saved.getAccountHolderName(), saved.getIfscCode());

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_UPDATED",
                "ACCOUNT",
                accountNumber,
                prevDetails,
                newDetails,
                ipAddress,
                "SUCCESS",
                String.format("Super Admin updated account #%s for customer %s (%s)",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A", newDetails)
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse activateAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_ACTIVATED",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.ACTIVE.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin activated account #%s for customer %s",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse deactivateAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.SUSPENDED);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_DEACTIVATED",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.SUSPENDED.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin deactivated/suspended account #%s for customer %s",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse freezeAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.SUSPENDED);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_FROZEN",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.SUSPENDED.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin froze account #%s for customer %s",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse unfreezeAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_UNFROZEN",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.ACTIVE.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin unfroze account #%s for customer %s",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public AccountResponse blockAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        String prevStatus = account.getStatus().name();
        account.setStatus(AccountStatus.CLOSED);
        Account saved = accountRepository.save(account);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "ACCOUNT_BLOCKED",
                "ACCOUNT",
                accountNumber,
                prevStatus,
                AccountStatus.CLOSED.name(),
                ipAddress,
                "SUCCESS",
                String.format("Super Admin permanently closed/blocked account #%s for customer %s",
                        accountNumber, saved.getUser() != null ? saved.getUser().getUsername() : "N/A")
        );

        return enrichAccountResponse(saved);
    }

    @Transactional
    public Map<String, Object> closeOrDeleteAccount(String accountNumber, String performedBy, String ipAddress) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        List<Transaction> transactions = transactionRepository.findAllByAccount(account);
        String customerUsername = account.getUser() != null ? account.getUser().getUsername() : "unknown";
        String prevStatus = account.getStatus().name();

        Map<String, Object> result = new HashMap<>();

        // Banking rule: If the account contains transactions or non-zero balance, do NOT hard-delete; safely CLOSE to preserve audit/financial trails
        if (!transactions.isEmpty() || (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) > 0)) {
            account.setStatus(AccountStatus.CLOSED);
            Account saved = accountRepository.save(account);

            // Cancel any associated debit card
            try {
                debitCardService.cancelCardForAccount(account, performedBy, ipAddress, "Account closed by Super Admin");
            } catch (Exception ignored) {}

            auditLogService.logAction(
                    performedBy,
                    "ROLE_SUPER_ADMIN",
                    "ACCOUNT_CLOSED",
                    "ACCOUNT",
                    accountNumber,
                    prevStatus,
                    AccountStatus.CLOSED.name(),
                    ipAddress,
                    "SUCCESS",
                    String.format("Super Admin safely closed account #%s for customer %s (preserved %d transactions and ledger records)",
                            accountNumber, customerUsername, transactions.size())
            );

            result.put("accountNumber", accountNumber);
            result.put("customer", customerUsername);
            result.put("status", "CLOSED");
            result.put("softDeleted", true);
            result.put("permanentlyDeleted", false);
            result.put("message", "Account contains financial transaction history (" + transactions.size() + " transactions). It has been safely CLOSED to preserve financial and audit records.");
            result.put("account", enrichAccountResponse(saved));
        } else {
            // Clean up any unissued/provisioned debit card first
            try {
                debitCardRepository.findByAccount(account).ifPresent(debitCardRepository::delete);
            } catch (Exception ignored) {}

            // No transactions and zero balance: permanently remove unused account
            accountRepository.delete(account);

            auditLogService.logAction(
                    performedBy,
                    "ROLE_SUPER_ADMIN",
                    "ACCOUNT_DELETED",
                    "ACCOUNT",
                    accountNumber,
                    prevStatus,
                    "DELETED",
                    ipAddress,
                    "SUCCESS",
                    String.format("Super Admin permanently deleted unused account #%s for customer %s (zero transactions)",
                            accountNumber, customerUsername)
            );

            result.put("accountNumber", accountNumber);
            result.put("customer", customerUsername);
            result.put("status", "DELETED");
            result.put("softDeleted", false);
            result.put("permanentlyDeleted", true);
            result.put("message", "Account #" + accountNumber + " had no transactions and has been permanently deleted.");
        }

        return result;
    }

    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return transactionService.getTransactionHistory(accountNumber);
    }

    private AccountResponse enrichAccountResponse(Account account) {
        AccountResponse resp = accountService.convertToResponse(account);
        List<Transaction> txs = transactionRepository.findAllByAccount(account);
        resp.setTransactionCount(txs.size());
        return resp;
    }

    private String generateUniqueAccountNumber() {
        Random random = new Random();
        String accountNumber;
        do {
            long num = 1000000000L + (long)(random.nextDouble() * 9000000000L);
            accountNumber = String.valueOf(num);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    // ================= TRANSACTION & FRAUD MANAGEMENT =================
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    public List<TransactionResponse> getSuspiciousTransactions() {
        return transactionService.getAllTransactions().stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel())
                        || "CRITICAL".equalsIgnoreCase(t.getRiskLevel())
                        || "BLOCKED".equalsIgnoreCase(t.getStatus())
                        || "UNDER_REVIEW".equalsIgnoreCase(t.getStatus()))
                .toList();
    }

    @Transactional
    public TransactionResponse blockTransaction(String identifier, String performedBy, String ipAddress) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.BLOCKED);
        tx.setRiskLevel(RiskLevel.CRITICAL);
        tx.setRiskReasons("Manually reviewed and BLOCKED by Super Admin.");
        Transaction saved = transactionRepository.save(tx);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "TRANSACTION_BLOCKED",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.BLOCKED.name(),
                ipAddress,
                "SUCCESS",
                "Super Admin blocked transaction reference " + saved.getTransactionReference()
        );

        return transactionService.convertToResponse(saved);
    }

    @Transactional
    public TransactionResponse reviewTransaction(String identifier, String performedBy, String ipAddress) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.UNDER_REVIEW);
        Transaction saved = transactionRepository.save(tx);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "TRANSACTION_UNDER_REVIEW",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.UNDER_REVIEW.name(),
                ipAddress,
                "SUCCESS",
                "Super Admin marked transaction under review: " + saved.getTransactionReference()
        );

        return transactionService.convertToResponse(saved);
    }

    @Transactional
    public TransactionResponse markTransactionSafe(String identifier, String performedBy, String ipAddress) {
        Transaction tx = findTransaction(identifier);
        String prevStatus = tx.getStatus().name();

        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setRiskLevel(RiskLevel.LOW);
        tx.setRiskReasons("Manually reviewed and cleared as SAFE by Super Admin.");
        Transaction saved = transactionRepository.save(tx);

        auditLogService.logAction(
                performedBy,
                "ROLE_SUPER_ADMIN",
                "TRANSACTION_MARKED_SAFE",
                "TRANSACTION",
                saved.getTransactionReference(),
                prevStatus,
                TransactionStatus.COMPLETED.name(),
                ipAddress,
                "SUCCESS",
                "Super Admin marked transaction safe: " + saved.getTransactionReference()
        );

        return transactionService.convertToResponse(saved);
    }

    // ================= DEBIT CARD GOVERNANCE =================
    @Transactional
    public DebitCardBackfillResultDTO backfillDebitCards(String performedBy, String ipAddress) {
        return debitCardService.backfillMissingDebitCards(performedBy, ipAddress);
    }

    public List<DebitCardResponse> getAllDebitCards() {
        return debitCardService.getAllDebitCards();
    }

    @Transactional
    public DebitCardResponse updateDebitCardStatus(Long cardId, CardStatus status, String performedBy, String ipAddress, String reason) {
        return debitCardService.updateCardStatusByAdmin(cardId, status, performedBy, ipAddress, reason);
    }

    // ================= HELPERS =================
    private User findUser(String identifier) {
        Optional<User> byUsername = userRepository.findByUsername(identifier);
        if (byUsername.isPresent()) {
            return byUsername.get();
        }
        try {
            Long id = Long.parseLong(identifier);
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + identifier));
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("User not found: " + identifier);
        }
    }

    private Transaction findTransaction(String identifier) {
        Optional<Transaction> txOpt = transactionRepository.findByTransactionReference(identifier);
        if (txOpt.isPresent()) {
            return txOpt.get();
        }
        try {
            Long id = Long.parseLong(identifier);
            return transactionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + identifier));
        } catch (NumberFormatException ex) {
            throw new ResourceNotFoundException("Transaction not found with reference: " + identifier);
        }
    }
}