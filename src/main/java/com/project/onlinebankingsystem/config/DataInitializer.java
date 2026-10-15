package com.project.onlinebankingsystem.config;

import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.model.*;
import com.project.onlinebankingsystem.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.project.onlinebankingsystem.service.DebitCardService debitCardService;

    public DataInitializer(UserRepository userRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           BeneficiaryRepository beneficiaryRepository,
                           LoginActivityRepository loginActivityRepository,
                           NotificationRepository notificationRepository,
                           PasswordEncoder passwordEncoder,
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                           com.project.onlinebankingsystem.service.DebitCardService debitCardService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.loginActivityRepository = loginActivityRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.debitCardService = debitCardService;
    }

    @Override
    public void run(String... args) {
        // Ensure users.enabled column exists and is populated with default TRUE
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE");
        } catch (Exception e1) {
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE");
            } catch (Exception ignored) {
            }
        }
        try {
            jdbcTemplate.execute("UPDATE users SET enabled = TRUE WHERE enabled IS NULL");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role SET DATA TYPE VARCHAR(50)");
        } catch (Exception ignored) {
        }

        // Ensure transactions.status column allows new statuses (BLOCKED, PENDING_VERIFICATION) in H2
        try {
            jdbcTemplate.execute("ALTER TABLE transactions ALTER COLUMN status SET DATA TYPE VARCHAR(50)");
        } catch (Exception e1) {
            try {
                jdbcTemplate.execute("ALTER TABLE transactions ALTER COLUMN status VARCHAR(50)");
            } catch (Exception ignored) {
            }
        }

        // Ensure transactions.type column allows new transaction types (UPI_TRANSFER, UPI_REQUEST_PAYMENT, UPI_QR_PAYMENT) in H2
        try {
            jdbcTemplate.execute("ALTER TABLE transactions ALTER COLUMN type SET DATA TYPE VARCHAR(50)");
        } catch (Exception e1) {
            try {
                jdbcTemplate.execute("ALTER TABLE transactions ALTER COLUMN type VARCHAR(50)");
            } catch (Exception ignored) {
            }
        }

        // Auto-backfill account_holder_name for all existing accounts in database
        try {
            jdbcTemplate.execute("UPDATE accounts SET account_holder_name = (SELECT u.full_name FROM users u WHERE u.id = accounts.user_id) WHERE account_holder_name IS NULL");
        } catch (Exception ignored) {
        }

        // Auto-backfill risk metrics for all existing transactions in database
        try {
            jdbcTemplate.execute("UPDATE transactions SET risk_score = 10 WHERE risk_score IS NULL");
            jdbcTemplate.execute("UPDATE transactions SET risk_level = 'LOW' WHERE risk_level IS NULL");
            jdbcTemplate.execute("UPDATE transactions SET decision = 'ALLOW' WHERE decision IS NULL");
            jdbcTemplate.execute("UPDATE transactions SET fraud_probability = 10.0 WHERE fraud_probability IS NULL");
            jdbcTemplate.execute("UPDATE transactions SET rule_based_score = 10 WHERE rule_based_score IS NULL");
        } catch (Exception ignored) {
        }

        // 1. Always ensure dedicated Super Admin and Bank Admin exist
        ensureDedicatedSuperAdminExists();
        ensureDedicatedAdminExists();

        // 2. Initialize demo customer data if customers are not yet seeded
        if (userRepository.count() <= 2) {
            initializeDemoCustomerData();
        } else if (notificationRepository.count() == 0) {
            userRepository.findByUsername("rahul").ifPresent(this::seedNotificationsForUser);
        }

        // 3. Automatically ensure all existing and newly seeded bank accounts have unique Debit Cards generated
        try {
            debitCardService.backfillMissingDebitCards("SYSTEM_BOOTSTRAP", "127.0.0.1");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DataInitializer.class).warn("Initial debit card backfill on bootstrap: {}", e.getMessage());
        }
    }

    @Transactional
    public void ensureDedicatedSuperAdminExists() {
        Optional<User> superAdminOpt = userRepository.findByUsername("superadmin");
        if (superAdminOpt.isEmpty()) {
            User superAdmin = User.builder()
                    .username("superadmin")
                    .password(passwordEncoder.encode("SuperAdmin@123"))
                    .fullName("Bank Super Administrator")
                    .email("superadmin@smartbank.ai")
                    .phone("9999900000")
                    .fatherName("Board of Governors")
                    .motherName("Central Oversight Council")
                    .dob(LocalDate.of(1980, 1, 1))
                    .pan("SUPAD1234S")
                    .aadhar("111100009999")
                    .gender(Gender.OTHERS)
                    .address("Bank Corporate Headquarters, Executive Tower, Mumbai")
                    .role(Role.ROLE_SUPER_ADMIN)
                    .accountSecurityStatus("GOOD")
                    .failedLoginAttempts(0)
                    .enabled(true)
                    .lastLoginTime(LocalDateTime.now().minusHours(1))
                    .transactionPin(passwordEncoder.encode("9999"))
                    .build();
            userRepository.save(superAdmin);
        } else {
            User superAdmin = superAdminOpt.get();
            boolean changed = false;
            if (superAdmin.getRole() != Role.ROLE_SUPER_ADMIN) {
                superAdmin.setRole(Role.ROLE_SUPER_ADMIN);
                changed = true;
            }
            if (superAdmin.getEnabled() == null || !superAdmin.getEnabled()) {
                superAdmin.setEnabled(true);
                changed = true;
            }
            if (!"GOOD".equalsIgnoreCase(superAdmin.getAccountSecurityStatus())) {
                superAdmin.setAccountSecurityStatus("GOOD");
                changed = true;
            }
            if (superAdmin.getFailedLoginAttempts() != null && superAdmin.getFailedLoginAttempts() > 0) {
                superAdmin.setFailedLoginAttempts(0);
                changed = true;
            }
            if (superAdmin.getPassword() == null || !passwordEncoder.matches("SuperAdmin@123", superAdmin.getPassword())) {
                superAdmin.setPassword(passwordEncoder.encode("SuperAdmin@123"));
                changed = true;
            }
            if (changed) {
                userRepository.save(superAdmin);
            }
        }

        // Guarantee that ONLY 'superadmin' can ever hold ROLE_SUPER_ADMIN
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            if (!"superadmin".equalsIgnoreCase(u.getUsername()) && u.getRole() == Role.ROLE_SUPER_ADMIN) {
                u.setRole(Role.ROLE_CUSTOMER);
                userRepository.save(u);
            }
        }
    }

    @Transactional
    public void ensureDedicatedAdminExists() {
        Optional<User> adminOpt = userRepository.findByUsername("admin");
        if (adminOpt.isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@123"))
                    .fullName("System Administrator")
                    .email("admin@smartbank.ai")
                    .phone("9999900001")
                    .fatherName("Admin Sr")
                    .motherName("Admin Mother")
                    .dob(LocalDate.of(1985, 1, 1))
                    .pan("ADMIN1234A")
                    .aadhar("999988887777")
                    .gender(Gender.OTHERS)
                    .address("SmartBank AI Headquarters, Cyber City, Bangalore")
                    .role(Role.ROLE_ADMIN)
                    .accountSecurityStatus("GOOD")
                    .failedLoginAttempts(0)
                    .enabled(true)
                    .lastLoginTime(LocalDateTime.now().minusHours(2))
                    .transactionPin(passwordEncoder.encode("1234"))
                    .build();
            userRepository.save(admin);
        } else {
            User existingAdmin = adminOpt.get();
            boolean changed = false;
            if (existingAdmin.getRole() != Role.ROLE_ADMIN) {
                existingAdmin.setRole(Role.ROLE_ADMIN);
                changed = true;
            }
            if (existingAdmin.getEnabled() == null || !existingAdmin.getEnabled()) {
                existingAdmin.setEnabled(true);
                changed = true;
            }
            if (existingAdmin.getPassword() == null || !passwordEncoder.matches("Admin@123", existingAdmin.getPassword())) {
                existingAdmin.setPassword(passwordEncoder.encode("Admin@123"));
                changed = true;
            }
            if (changed) {
                userRepository.save(existingAdmin);
            }
        }
    }

    @Transactional
    public void initializeDemoCustomerData() {
        // 1. Create Primary Demo Customer "rahul"
        User rahul = User.builder()
                .username("rahul")
                .password(passwordEncoder.encode("Rahul@123"))
                .fullName("Rahul Sharma")
                .email("rahul.sharma@example.com")
                .phone("9876543210")
                .fatherName("Mahesh Sharma")
                .motherName("Sunita Sharma")
                .dob(LocalDate.of(1994, 6, 15))
                .pan("ABCDE1234F")
                .aadhar("123456789012")
                .gender(Gender.MALE)
                .address("Flat 402, Skyline Residency, Bandra West, Mumbai 400050")
                .role(Role.ROLE_CUSTOMER)
                .accountSecurityStatus("GOOD")
                .failedLoginAttempts(0)
                .lastLoginTime(LocalDateTime.now().minusMinutes(45))
                .transactionPin(passwordEncoder.encode("1234"))
                .build();
        User savedRahul = userRepository.save(rahul);

        // 2. Create Secondary Customer "priya" (for cross-account transfers)
        User priya = User.builder()
                .username("priya")
                .password(passwordEncoder.encode("Priya@123"))
                .fullName("Priya Patel")
                .email("priya.patel@example.com")
                .phone("9822334455")
                .fatherName("Ramesh Patel")
                .motherName("Geeta Patel")
                .dob(LocalDate.of(1996, 3, 20))
                .pan("XYZPQ5678K")
                .aadhar("987654321098")
                .gender(Gender.FEMALE)
                .address("Villa 12, Green Palms, Ahmedabad 380015")
                .role(Role.ROLE_CUSTOMER)
                .accountSecurityStatus("GOOD")
                .failedLoginAttempts(0)
                .transactionPin(passwordEncoder.encode("1234"))
                .build();
        User savedPriya = userRepository.save(priya);

        // 3. Create Accounts
        Account rahulAccount = Account.builder()
                .accountNumber("1002345678")
                .ifscCode("SBIN0001010")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("125430.50"))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusMonths(6))
                .accountHolderName(savedRahul.getFullName())
                .user(savedRahul)
                .build();
        accountRepository.save(rahulAccount);

        Account priyaAccount = Account.builder()
                .accountNumber("2009876543")
                .ifscCode("SBIN0001010")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("45200.00"))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusMonths(4))
                .accountHolderName(savedPriya.getFullName())
                .user(savedPriya)
                .build();
        accountRepository.save(priyaAccount);

        // 4. Create Beneficiaries for Rahul
        Beneficiary ben1 = Beneficiary.builder()
                .name("Priya Patel")
                .accountNumber("2009876543")
                .ifscCode("SBIN0001010")
                .bankName("SmartBank AI")
                .user(savedRahul)
                .createdAt(LocalDateTime.now().minusMonths(2))
                .confirmed(true)
                .transferCount(5)
                .totalTransferred(new BigDecimal("20400.00"))
                .lastTransactionAt(LocalDateTime.now().minusDays(2))
                .riskStatus("VERIFIED")
                .build();
        beneficiaryRepository.save(ben1);

        Beneficiary ben2 = Beneficiary.builder()
                .name("Amit Verma")
                .accountNumber("4098231456")
                .ifscCode("HDFC0001234")
                .bankName("HDFC Bank")
                .user(savedRahul)
                .createdAt(LocalDateTime.now().minusMonths(1))
                .confirmed(true)
                .transferCount(2)
                .totalTransferred(new BigDecimal("12000.00"))
                .lastTransactionAt(LocalDateTime.now().minusDays(12))
                .riskStatus("NORMAL")
                .build();
        beneficiaryRepository.save(ben2);

        Beneficiary ben3 = Beneficiary.builder()
                .name("TechCorp Solutions")
                .accountNumber("9876123450")
                .ifscCode("ICIC0005678")
                .bankName("ICICI Bank")
                .user(savedRahul)
                .createdAt(LocalDateTime.now().minusDays(15))
                .confirmed(true)
                .transferCount(1)
                .totalTransferred(new BigDecimal("8500.00"))
                .lastTransactionAt(LocalDateTime.now().minusDays(10))
                .riskStatus("NORMAL")
                .build();
        beneficiaryRepository.save(ben3);

        Beneficiary ben4 = Beneficiary.builder()
                .name("Sneha Rao (New)")
                .accountNumber("5544332211")
                .ifscCode("AXIS0009876")
                .bankName("Axis Bank")
                .user(savedRahul)
                .createdAt(LocalDateTime.now().minusHours(3))
                .confirmed(true)
                .transferCount(0)
                .totalTransferred(BigDecimal.ZERO)
                .riskStatus("NEW")
                .build();
        beneficiaryRepository.save(ben4);

        // 5. Create Historical Transactions across categories and risk levels
        LocalDateTime now = LocalDateTime.now();

        // Previous Month Transactions
        createTx(rahulAccount, null, new BigDecimal("150000.00"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS, "Initial Corporate Salary Credit", now.minusMonths(1).minusDays(10), 5, RiskLevel.LOW, "Payroll verified", ExpenseCategory.OTHER, false, FraudDecision.ALLOW, 5, 5.0, 0.05);
        createTx(rahulAccount, null, new BigDecimal("4500.00"), TransactionType.WITHDRAWAL, TransactionStatus.SUCCESS, "ATM Cash Withdrawal Bandra", now.minusMonths(1).minusDays(8), 10, RiskLevel.LOW, "Standard ATM withdrawal", ExpenseCategory.OTHER, false, FraudDecision.ALLOW, 10, 10.0, 0.10);
        createTx(rahulAccount, priyaAccount, new BigDecimal("5000.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Swiggy & Dining expenses split", now.minusMonths(1).minusDays(6), 12, RiskLevel.LOW, "Normal peer transfer", ExpenseCategory.FOOD, false, FraudDecision.ALLOW, 12, 12.0, 0.12);
        createTx(rahulAccount, priyaAccount, new BigDecimal("6200.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Zara & Amazon shopping reimbursement", now.minusMonths(1).minusDays(4), 18, RiskLevel.LOW, "Consistent recipient", ExpenseCategory.SHOPPING, false, FraudDecision.ALLOW, 18, 18.0, 0.18);
        createTx(rahulAccount, null, new BigDecimal("3500.00"), TransactionType.WITHDRAWAL, TransactionStatus.SUCCESS, "Electricity & Internet broadband bill payment", now.minusMonths(1).minusDays(2), 10, RiskLevel.LOW, "Recurring utility bill", ExpenseCategory.BILLS, false, FraudDecision.ALLOW, 10, 10.0, 0.10);

        // Current Month Transactions
        createTx(rahulAccount, null, new BigDecimal("65000.00"), TransactionType.DEPOSIT, TransactionStatus.SUCCESS, "Monthly Salary & Performance Bonus", now.minusDays(12), 5, RiskLevel.LOW, "Regular salary inflow", ExpenseCategory.OTHER, false, FraudDecision.ALLOW, 5, 5.0, 0.05);
        createTx(rahulAccount, priyaAccount, new BigDecimal("2500.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Weekend Dinner at Royal Cafe", now.minusDays(9), 12, RiskLevel.LOW, "Normal pattern, recognized beneficiary", ExpenseCategory.FOOD, false, FraudDecision.ALLOW, 12, 12.0, 0.12);
        createTx(rahulAccount, priyaAccount, new BigDecimal("4800.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Amazon Electronics & Gadgets shopping", now.minusDays(7), 22, RiskLevel.LOW, "Standard personal transfer", ExpenseCategory.SHOPPING, false, FraudDecision.ALLOW, 20, 24.0, 0.22);
        createTx(rahulAccount, null, new BigDecimal("2800.00"), TransactionType.WITHDRAWAL, TransactionStatus.SUCCESS, "Airtel Fiber Broadband & Mobile recharge bills", now.minusDays(5), 10, RiskLevel.LOW, "Verified bill payment", ExpenseCategory.BILLS, false, FraudDecision.ALLOW, 10, 10.0, 0.10);
        createTx(rahulAccount, priyaAccount, new BigDecimal("1900.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "Uber airport taxi travel commute", now.minusDays(4), 15, RiskLevel.LOW, "Routine travel expense", ExpenseCategory.TRAVEL, false, FraudDecision.ALLOW, 15, 15.0, 0.15);
        createTx(rahulAccount, null, new BigDecimal("3200.00"), TransactionType.WITHDRAWAL, TransactionStatus.SUCCESS, "Apollo Pharmacy healthcare & medical checkup", now.minusDays(3), 14, RiskLevel.LOW, "Health clinic bill payment", ExpenseCategory.HEALTHCARE, false, FraudDecision.ALLOW, 14, 14.0, 0.14);
        createTx(rahulAccount, priyaAccount, new BigDecimal("1200.00"), TransactionType.TRANSFER, TransactionStatus.SUCCESS, "PVR Cinemas Movie Tickets & Snacks", now.minusDays(2), 16, RiskLevel.LOW, "Weekend entertainment", ExpenseCategory.ENTERTAINMENT, false, FraudDecision.ALLOW, 15, 17.0, 0.16);

        // Suspicious Transaction Under Review (for Admin Fraud Dashboard demonstration)
        createTx(rahulAccount, priyaAccount, new BigDecimal("42000.00"), TransactionType.TRANSFER, TransactionStatus.UNDER_REVIEW, "Urgent Contractor Consulting Fee", now.minusHours(6), 56, RiskLevel.MEDIUM, "⚠️ Transaction Under Review: Amount 2.8x higher than 30-day average; Stepped up to risk level MEDIUM; Flagged for compliance review.", ExpenseCategory.OTHER, true, FraudDecision.REVIEW, 55, 57.0, 0.56);

        // High Risk Sample Blocked Transaction (for demonstration in Fraud/Security consoles)
        createTx(rahulAccount, priyaAccount, new BigDecimal("85000.00"), TransactionType.TRANSFER, TransactionStatus.BLOCKED, "Attempted Large Midnight Transfer [BLOCKED]", now.minusHours(14), 89, RiskLevel.HIGH, "❌ Transaction Blocked: Unusually high amount (4.5x average); Late-night timing (02:18 AM); Verification OTP not confirmed.", ExpenseCategory.OTHER, false, FraudDecision.BLOCK, 85, 92.0, 0.89);

        // 6. Seed Notifications for Rahul
        seedNotificationsForUser(savedRahul);

        // 7. Create Login Activities
        loginActivityRepository.save(LoginActivity.builder()
                .username("rahul")
                .ipAddress("192.168.1.15")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0.0.0")
                .timestamp(LocalDateTime.now().minusMinutes(45))
                .status("SUCCESS")
                .build());

        loginActivityRepository.save(LoginActivity.builder()
                .username("rahul")
                .ipAddress("192.168.1.15")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0.0.0")
                .timestamp(LocalDateTime.now().minusDays(1).minusHours(3))
                .status("SUCCESS")
                .build());

        loginActivityRepository.save(LoginActivity.builder()
                .username("rahul")
                .ipAddress("103.21.14.88")
                .userAgent("Unknown Device / Python-requests")
                .timestamp(LocalDateTime.now().minusDays(2))
                .status("FAILED")
                .failureReason("Bad credentials")
                .build());
    }

    public void seedNotificationsForUser(User user) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.TRANSACTION_SUCCESS)
                .title("Salary Credited")
                .message("₹65,000.00 has been credited to your account 1002345678 (Ref: Monthly Salary & Bonus).")
                .isRead(false)
                .createdAt(LocalDateTime.now().minusDays(12))
                .actionUrl("/dashboard")
                .build());

        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.FRAUD_ALERT)
                .title("High-Risk Transfer Blocked")
                .message("An anomalous transfer of ₹85,000.00 was blocked at 02:18 AM due to high deviation from spending pattern and unverified OTP.")
                .isRead(false)
                .createdAt(LocalDateTime.now().minusHours(14))
                .actionUrl("/fraud-detection")
                .build());

        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.SECURITY_ALERT)
                .title("Failed Login Attempt Detected")
                .message("An unauthorized login attempt was blocked from IP 103.21.14.88 (Unrecognized device). Your account remains secure.")
                .isRead(false)
                .createdAt(LocalDateTime.now().minusDays(2))
                .actionUrl("/security")
                .build());

        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.SPENDING_ALERT)
                .title("Smart Spending Alert")
                .message("You have spent 68% of your predicted monthly budget. Consider reviewing shopping expenses to maximize savings.")
                .isRead(true)
                .createdAt(LocalDateTime.now().minusDays(3))
                .actionUrl("/financial-health")
                .build());

        notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.BENEFICIARY_ADDED)
                .title("New Beneficiary Registered")
                .message("Beneficiary 'Sneha Rao' (AXIS0009876) was added. New beneficiaries are subject to enhanced security monitoring for the first 3 transfers.")
                .isRead(true)
                .createdAt(LocalDateTime.now().minusHours(3))
                .actionUrl("/beneficiaries")
                .build());
    }

    private void createTx(Account source, Account dest, BigDecimal amount,
                          TransactionType type, TransactionStatus status,
                          String description, LocalDateTime timestamp,
                          int riskScore, RiskLevel riskLevel, String reasons,
                          ExpenseCategory category, boolean verifiedOtp,
                          FraudDecision decision, int ruleScore, Double mlScore, Double fraudProb) {
        Transaction tx = Transaction.builder()
                .transactionReference("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .sourceAccount(source)
                .destinationAccount(dest)
                .amount(amount)
                .type(type)
                .status(status)
                .description(description)
                .timestamp(timestamp)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .riskReasons(reasons)
                .category(category)
                .verifiedWithOtp(verifiedOtp)
                .decision(decision)
                .ruleBasedScore(ruleScore)
                .mlPrediction(mlScore)
                .fraudProbability(fraudProb)
                .fraudCheckedAt(timestamp)
                .build();
        transactionRepository.save(tx);
    }
}
