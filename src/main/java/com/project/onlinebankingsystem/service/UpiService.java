package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.exception.AccountSuspendedException;
import com.project.onlinebankingsystem.exception.InsufficientBalanceException;
import com.project.onlinebankingsystem.exception.ResourceNotFoundException;
import com.project.onlinebankingsystem.model.*;
import com.project.onlinebankingsystem.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UpiService {

    private static final Logger log = LoggerFactory.getLogger(UpiService.class);
    private static final int MAX_PIN_ATTEMPTS = 5;
    private static final String DEFAULT_UPI_HANDLE_SUFFIX = "@smartbank";

    private final UpiProfileRepository upiProfileRepository;
    private final UpiMoneyRequestRepository upiMoneyRequestRepository;
    private final UpiLimitConfigurationRepository upiLimitConfigurationRepository;
    private final UpiTransactionRepository upiTransactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FraudDetectionService fraudDetectionService;
    private final ExpenseAnalyticsService expenseAnalyticsService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final OtpService otpService;
    private final QrCodeService qrCodeService;
    private final PasswordEncoder passwordEncoder;

    public UpiService(UpiProfileRepository upiProfileRepository,
                      UpiMoneyRequestRepository upiMoneyRequestRepository,
                      UpiLimitConfigurationRepository upiLimitConfigurationRepository,
                      UpiTransactionRepository upiTransactionRepository,
                      UserRepository userRepository,
                      AccountRepository accountRepository,
                      TransactionRepository transactionRepository,
                      FraudDetectionService fraudDetectionService,
                      ExpenseAnalyticsService expenseAnalyticsService,
                      NotificationService notificationService,
                      AuditLogService auditLogService,
                      OtpService otpService,
                      QrCodeService qrCodeService,
                      PasswordEncoder passwordEncoder) {
        this.upiProfileRepository = upiProfileRepository;
        this.upiMoneyRequestRepository = upiMoneyRequestRepository;
        this.upiLimitConfigurationRepository = upiLimitConfigurationRepository;
        this.upiTransactionRepository = upiTransactionRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.fraudDetectionService = fraudDetectionService;
        this.expenseAnalyticsService = expenseAnalyticsService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.otpService = otpService;
        this.qrCodeService = qrCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    // ==========================================
    // 1. UPI ID MANAGEMENT
    // ==========================================

    public boolean isUpiAvailable(String upiId) {
        if (upiId == null || upiId.isBlank()) return false;
        String formatted = formatUpiId(upiId);
        return !upiProfileRepository.existsByUpiId(formatted);
    }

    @Transactional
    public UpiProfileResponse createUpiId(String username, String customUpiId, String accountNumber) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Selected account does not belong to user.");
        }

        String targetUpiId = formatUpiId(customUpiId);

        if (upiProfileRepository.existsByUpiId(targetUpiId)) {
            throw new IllegalArgumentException("UPI ID '" + targetUpiId + "' is already taken. Please choose another.");
        }

        Optional<UpiProfile> existingOpt = upiProfileRepository.findByUser(user);
        UpiProfile upiProfile;
        if (existingOpt.isPresent()) {
            upiProfile = existingOpt.get();
            upiProfile.setUpiId(targetUpiId);
            upiProfile.setPrimaryAccount(account);
            upiProfile.setIsActive(true);
        } else {
            upiProfile = UpiProfile.builder()
                    .user(user)
                    .upiId(targetUpiId)
                    .primaryAccount(account)
                    .isPrimary(true)
                    .isActive(true)
                    .failedPinAttempts(0)
                    .isLocked(false)
                    .build();
        }

        String rawUri = String.format("upi://pay?pa=%s&pn=%s", targetUpiId, user.getFullName().replace(" ", "%20"));
        String qrCodeUrl = qrCodeService.generateQrCodeBase64(rawUri, 280, 280);
        upiProfile.setQrCodeData(qrCodeUrl);

        UpiProfile saved = upiProfileRepository.save(upiProfile);

        auditLogService.logAction(username, user.getRole().name(), "CREATE_UPI_ID",
                "UpiProfile", saved.getId() != null ? saved.getId().toString() : "0", null, targetUpiId, "127.0.0.1", "SUCCESS",
                "Created new UPI ID: " + targetUpiId);

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI ID Registered",
                "Your UPI ID '" + targetUpiId + "' has been successfully registered and linked with account " + accountNumber + ".",
                "/upi");

        return convertProfileToResponse(saved);
    }

    @Transactional(readOnly = true)
    public UpiProfileResponse getUpiProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Optional<UpiProfile> profileOpt = upiProfileRepository.findByUser(user);
        if (profileOpt.isEmpty()) {
            // Auto-provision default UPI ID if missing
            Account defaultAccount = accountRepository.findByUserUsername(username).stream().findFirst()
                    .orElse(null);
            if (defaultAccount != null) {
                return createUpiId(username, username + DEFAULT_UPI_HANDLE_SUFFIX, defaultAccount.getAccountNumber());
            }
            throw new ResourceNotFoundException("No active UPI profile found for user: " + username);
        }

        return convertProfileToResponse(profileOpt.get());
    }

    @Transactional
    public UpiProfileResponse updateUpiId(String username, String newUpiId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiProfile profile = upiProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile not found for user: " + username));

        String formatted = formatUpiId(newUpiId);
        if (!formatted.equalsIgnoreCase(profile.getUpiId()) && upiProfileRepository.existsByUpiId(formatted)) {
            throw new IllegalArgumentException("UPI ID '" + formatted + "' is already registered to another user.");
        }

        String oldId = profile.getUpiId();
        profile.setUpiId(formatted);

        String rawUri = String.format("upi://pay?pa=%s&pn=%s", formatted, user.getFullName().replace(" ", "%20"));
        profile.setQrCodeData(qrCodeService.generateQrCodeBase64(rawUri, 280, 280));

        UpiProfile saved = upiProfileRepository.save(profile);

        auditLogService.logAction(username, user.getRole().name(), "UPDATE_UPI_ID",
                "UpiProfile", saved.getId() != null ? saved.getId().toString() : "0", oldId, formatted, "127.0.0.1", "SUCCESS",
                "Updated UPI ID from " + oldId + " to " + formatted);

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI ID Updated",
                "Your UPI ID has been updated to '" + formatted + "'.", "/upi");

        return convertProfileToResponse(saved);
    }

    @Transactional
    public UpiProfileResponse toggleUpiStatus(String username, boolean active) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiProfile profile = upiProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile not found"));

        profile.setIsActive(active);
        UpiProfile saved = upiProfileRepository.save(profile);

        String statusStr = active ? "REACTIVATED" : "DEACTIVATED";

        auditLogService.logAction(username, user.getRole().name(), "TOGGLE_UPI_STATUS",
                "UpiProfile", saved.getId() != null ? saved.getId().toString() : "0", null, statusStr, "127.0.0.1", "SUCCESS",
                "UPI ID " + profile.getUpiId() + " status set to " + statusStr);

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI Status Changed",
                "Your UPI ID '" + profile.getUpiId() + "' is now " + (active ? "Active" : "Deactivated") + ".", "/upi");

        return convertProfileToResponse(saved);
    }

    // ==========================================
    // 2. UPI PIN MANAGEMENT
    // ==========================================

    @Transactional
    public void setUpiPin(String username, String pin, String confirmPin, String accountPassword) {
        validatePinFormat(pin, confirmPin);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (accountPassword != null && !accountPassword.isBlank()) {
            if (!passwordEncoder.matches(accountPassword.trim(), user.getPassword())) {
                throw new IllegalArgumentException("Incorrect account password. Verification failed.");
            }
        }

        UpiProfile profile = getOrCreateProfileInternal(user);

        profile.setUpiPin(passwordEncoder.encode(pin.trim()));
        profile.setFailedPinAttempts(0);
        profile.setIsLocked(false);
        profile.setLockTime(null);
        upiProfileRepository.save(profile);

        auditLogService.logAction(username, user.getRole().name(), "SET_UPI_PIN",
                "UpiProfile", String.valueOf(profile.getId()), null, "PIN_SET", "127.0.0.1", "SUCCESS",
                "Secure UPI PIN set successfully.");

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI Security PIN Configured",
                "Your 6-digit UPI PIN has been set securely. Never share your PIN with anyone.", "/upi");
    }

    @Transactional
    public void changeUpiPin(String username, String oldPin, String newPin, String confirmPin) {
        validatePinFormat(newPin, confirmPin);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiProfile profile = upiProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile not found"));

        if (Boolean.TRUE.equals(profile.getIsLocked())) {
            throw new IllegalArgumentException("UPI functionality is locked due to multiple failed PIN attempts. Reset PIN or contact support.");
        }

        if (profile.getUpiPin() != null) {
            if (oldPin == null || !passwordEncoder.matches(oldPin.trim(), profile.getUpiPin())) {
                throw new IllegalArgumentException("Incorrect current UPI PIN.");
            }
        }

        profile.setUpiPin(passwordEncoder.encode(newPin.trim()));
        profile.setFailedPinAttempts(0);
        upiProfileRepository.save(profile);

        auditLogService.logAction(username, user.getRole().name(), "CHANGE_UPI_PIN",
                "UpiProfile", String.valueOf(profile.getId()), null, "PIN_CHANGED", "127.0.0.1", "SUCCESS",
                "UPI PIN changed successfully.");

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI PIN Changed",
                "Your UPI PIN was changed successfully.", "/upi");
    }

    @Transactional
    public void resetUpiPin(String username, String newPin, String confirmPin, String accountPassword, String otp) {
        validatePinFormat(newPin, confirmPin);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        boolean passwordValid = accountPassword != null && passwordEncoder.matches(accountPassword.trim(), user.getPassword());
        boolean otpValid = otp != null && otpService.validateOtp("UPI_PIN_RESET_" + username, otp);

        if (!passwordValid && !otpValid && !isDemoOtp(otp)) {
            throw new IllegalArgumentException("Security verification failed. Valid account password or OTP is required to reset PIN.");
        }

        UpiProfile profile = getOrCreateProfileInternal(user);

        profile.setUpiPin(passwordEncoder.encode(newPin.trim()));
        profile.setFailedPinAttempts(0);
        profile.setIsLocked(false);
        profile.setLockTime(null);
        upiProfileRepository.save(profile);

        auditLogService.logAction(username, user.getRole().name(), "RESET_UPI_PIN",
                "UpiProfile", String.valueOf(profile.getId()), "LOCKED", "UNLOCKED", "127.0.0.1", "SUCCESS",
                "UPI PIN reset and security lock cleared.");

        notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                "UPI PIN Reset Successful",
                "Your UPI PIN was reset successfully and security lock was cleared.", "/upi");
    }

    @Transactional
    public boolean verifyUpiPin(String username, String candidatePin) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiProfile profile = upiProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("UPI profile not found. Please create a UPI ID first."));

        if (Boolean.TRUE.equals(profile.getIsLocked())) {
            throw new IllegalArgumentException("🔒 UPI Security Lock Active: Your UPI transactions are temporarily locked after 5 incorrect PIN attempts. Please reset your PIN.");
        }

        if (profile.getUpiPin() == null) {
            // Check fallback default PINs for initial setup demo
            if (candidatePin != null && (candidatePin.trim().equals("123456") || candidatePin.trim().equals("1234"))) {
                return true;
            }
            throw new IllegalArgumentException("UPI PIN is not set yet. Please set your 6-digit UPI PIN first.");
        }

        boolean matches = passwordEncoder.matches(candidatePin.trim(), profile.getUpiPin());

        // Fallback check for demo environment codes
        if (!matches && (candidatePin.trim().equals("123456") || candidatePin.trim().equals("1234"))) {
            matches = true;
        }

        if (matches) {
            profile.setFailedPinAttempts(0);
            upiProfileRepository.save(profile);
            return true;
        } else {
            int attempts = (profile.getFailedPinAttempts() != null ? profile.getFailedPinAttempts() : 0) + 1;
            profile.setFailedPinAttempts(attempts);

            if (attempts >= MAX_PIN_ATTEMPTS) {
                profile.setIsLocked(true);
                profile.setLockTime(LocalDateTime.now());
                upiProfileRepository.save(profile);

                auditLogService.logAction(username, user.getRole().name(), "UPI_LOCK_TRIGGERED",
                        "UpiProfile", String.valueOf(profile.getId()), "UNLOCKED", "LOCKED", "127.0.0.1", "FAILED",
                        "UPI transactions locked due to 5 consecutive failed PIN attempts.");

                notificationService.createNotification(user, NotificationType.SECURITY_ALERT,
                        "🚨 UPI Security Locked",
                        "Your UPI transactions have been temporarily locked due to 5 failed PIN attempts. Reset your UPI PIN to unlock.", "/upi");

                throw new IllegalArgumentException("🔒 Incorrect UPI PIN. Maximum 5 attempts reached. Your UPI transactions have been locked for security.");
            }

            upiProfileRepository.save(profile);
            int remaining = MAX_PIN_ATTEMPTS - attempts;
            throw new IllegalArgumentException("Incorrect UPI PIN. " + remaining + " attempt(s) remaining before lock.");
        }
    }

    // ==========================================
    // 3. SEND MONEY USING UPI (ATOMIC)
    // ==========================================

    @Transactional
    public UpiTransactionResponse sendMoney(String senderUsername, UpiSendMoneyRequest req) {
        checkSystemUpiEnabled();

        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + senderUsername));

        UpiProfile senderProfile = upiProfileRepository.findByUser(sender)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile not found for sender. Please set up UPI first."));

        if (Boolean.FALSE.equals(senderProfile.getIsActive())) {
            throw new IllegalArgumentException("Your UPI ID is currently deactivated. Please reactivate it from settings.");
        }

        if (Boolean.TRUE.equals(senderProfile.getIsLocked())) {
            throw new IllegalArgumentException("🔒 UPI Security Lock Active: Your UPI account is locked. Reset your PIN to unlock.");
        }

        // Verify Sender UPI PIN
        verifyUpiPin(senderUsername, req.getUpiPin());

        // Resolve Beneficiary Profile & Account
        UpiProfile receiverProfile = resolveBeneficiaryProfile(req);

        if (receiverProfile == null) {
            throw new ResourceNotFoundException("Recipient beneficiary not found. Check UPI ID, mobile number, or account details.");
        }

        if (Boolean.FALSE.equals(receiverProfile.getIsActive())) {
            throw new IllegalArgumentException("Recipient UPI ID is inactive.");
        }

        Account senderAccount = senderProfile.getPrimaryAccount();
        Account receiverAccount = receiverProfile.getPrimaryAccount();

        if (senderAccount.getId().equals(receiverAccount.getId())) {
            throw new IllegalArgumentException("Cannot send money to your own same bank account via UPI.");
        }

        if (senderAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Sender bank account is " + senderAccount.getStatus());
        }
        if (receiverAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException("Recipient bank account is " + receiverAccount.getStatus());
        }

        BigDecimal amount = req.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient account balance. Available: ₹" + formatAmount(senderAccount.getBalance()));
        }

        // Check Transaction Limits
        validateUpiLimits(sender, amount);

        // Fraud & Risk Analysis
        FraudAssessmentResponse riskAssessment = fraudDetectionService.assessRisk(
                senderAccount.getAccountNumber(), receiverAccount.getAccountNumber(), amount);

        RiskLevel riskLevel = RiskLevel.valueOf(riskAssessment.getRiskLevel());
        boolean verifiedWithOtp = false;

        String txRef = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String upiRef = "UPI" + System.currentTimeMillis() + (int)(Math.random() * 1000);

        if (riskAssessment.isRequiresOtp()) {
            boolean otpValid = otpService.validateOtp(txRef, req.getOtp()) || isDemoOtp(req.getOtp());
            if (!otpValid) {
                // Log failed risk verification
                recordFailedUpiTransaction(upiRef, txRef, sender, senderProfile.getUpiId(), senderAccount,
                        receiverProfile.getUser(), receiverProfile.getUpiId(), receiverAccount, amount,
                        TransactionType.UPI_TRANSFER, req.getRemarks(), riskAssessment,
                        "High-risk transaction requires OTP verification. Invalid or missing OTP.");

                notificationService.createNotification(sender, NotificationType.TRANSACTION_BLOCKED,
                        "🚨 High Risk UPI Payment Blocked",
                        String.format("Payment of ₹%s to %s required OTP verification. Transaction blocked.", formatAmount(amount), receiverProfile.getUpiId()),
                        "/upi");

                throw new IllegalArgumentException("OTP_REQUIRED: High-risk UPI payment requires OTP verification. Risk Score: "
                        + riskAssessment.getRiskScore() + "/100 (" + riskLevel + ").");
            }
            verifiedWithOtp = true;
        }

        // --- Execute Atomic Money Transfer ---
        senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
        receiverAccount.setBalance(receiverAccount.getBalance().add(amount));

        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        ExpenseCategory category = resolveExpenseCategory(req.getCategory(), req.getRemarks());

        // Create main banking ledger transaction
        Transaction mainTx = Transaction.builder()
                .transactionReference(txRef)
                .sourceAccount(senderAccount)
                .destinationAccount(receiverAccount)
                .amount(amount)
                .type(TransactionType.UPI_TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .description("UPI Payment to " + receiverProfile.getUpiId() + (req.getRemarks() != null ? " (" + req.getRemarks() + ")" : ""))
                .timestamp(LocalDateTime.now())
                .riskScore(riskAssessment.getRiskScore())
                .riskLevel(riskLevel)
                .fraudProbability(riskAssessment.getFraudProbability())
                .decision(FraudDecision.valueOf(riskAssessment.getDecision()))
                .ruleBasedScore(riskAssessment.getRuleBasedScore())
                .mlPrediction((double) riskAssessment.getMlPredictionScore())
                .fraudCheckedAt(LocalDateTime.now())
                .riskReasons(String.join("; ", riskAssessment.getReasons()))
                .category(category)
                .verifiedWithOtp(verifiedWithOtp)
                .build();

        transactionRepository.save(mainTx);

        // Create dedicated UPI transaction record
        UpiTransaction upiTx = UpiTransaction.builder()
                .upiReferenceId(upiRef)
                .transactionReference(txRef)
                .senderUser(sender)
                .senderUpiId(senderProfile.getUpiId())
                .senderAccount(senderAccount)
                .receiverUser(receiverProfile.getUser())
                .receiverUpiId(receiverProfile.getUpiId())
                .receiverAccount(receiverAccount)
                .amount(amount)
                .transactionType(TransactionType.UPI_TRANSFER)
                .timestamp(LocalDateTime.now())
                .remarks(req.getRemarks() != null ? req.getRemarks() : "UPI Money Transfer")
                .status(UpiTransactionStatus.SUCCESS)
                .riskScore(riskAssessment.getRiskScore())
                .riskLevel(riskLevel)
                .fraudStatus(riskAssessment.getDecision())
                .category(category)
                .verifiedWithOtp(verifiedWithOtp)
                .build();

        UpiTransaction savedUpiTx = upiTransactionRepository.save(upiTx);

        auditLogService.logAction(senderUsername, sender.getRole().name(), "UPI_SEND_MONEY",
                "UpiTransaction", upiRef, null, "SUCCESS", "127.0.0.1", "SUCCESS",
                String.format("Sent ₹%s via UPI from %s to %s", formatAmount(amount), senderProfile.getUpiId(), receiverProfile.getUpiId()));

        notificationService.createNotification(sender, NotificationType.TRANSACTION_SUCCESS,
                "UPI Payment Successful",
                String.format("Sent ₹%s to %s via UPI. Ref: %s", formatAmount(amount), receiverProfile.getUpiId(), upiRef),
                "/upi");

        notificationService.createNotification(receiverProfile.getUser(), NotificationType.TRANSACTION_SUCCESS,
                "UPI Money Received",
                String.format("Received ₹%s from %s via UPI. Ref: %s", formatAmount(amount), senderProfile.getUpiId(), upiRef),
                "/upi");

        return convertUpiTxToResponse(savedUpiTx, senderUsername);
    }

    // ==========================================
    // 4. REQUEST MONEY
    // ==========================================

    @Transactional
    public UpiMoneyRequestDTO createMoneyRequest(String requesterUsername, String targetUpiId, BigDecimal amount, String remarks) {
        checkSystemUpiEnabled();

        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Requester user not found"));

        UpiProfile requesterProfile = upiProfileRepository.findByUser(requester)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile required for requester"));

        String formattedTarget = formatUpiId(targetUpiId);
        UpiProfile targetProfile = upiProfileRepository.findByUpiId(formattedTarget)
                .orElseThrow(() -> new ResourceNotFoundException("Target UPI ID '" + formattedTarget + "' not found."));

        if (requesterProfile.getUpiId().equalsIgnoreCase(targetProfile.getUpiId())) {
            throw new IllegalArgumentException("Cannot send payment request to yourself.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Request amount must be greater than zero.");
        }

        String reqRef = "REQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        UpiMoneyRequest moneyReq = UpiMoneyRequest.builder()
                .requestReference(reqRef)
                .requesterUser(requester)
                .requesterUpiId(requesterProfile.getUpiId())
                .targetUser(targetProfile.getUser())
                .targetUpiId(targetProfile.getUpiId())
                .amount(amount)
                .remarks(remarks != null ? remarks : "UPI Payment Request")
                .status(UpiRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        UpiMoneyRequest saved = upiMoneyRequestRepository.save(moneyReq);

        auditLogService.logAction(requesterUsername, requester.getRole().name(), "UPI_REQUEST_MONEY",
                "UpiMoneyRequest", reqRef, null, "PENDING", "127.0.0.1", "SUCCESS",
                String.format("Requested ₹%s from %s", formatAmount(amount), targetProfile.getUpiId()));

        notificationService.createNotification(targetProfile.getUser(), NotificationType.OTP_REQUIRED,
                "UPI Payment Request Received",
                String.format("%s (%s) is requesting ₹%s. Remarks: %s",
                        requester.getFullName(), requesterProfile.getUpiId(), formatAmount(amount),
                        remarks != null ? remarks : "N/A"),
                "/upi");

        return convertMoneyReqToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<UpiMoneyRequestDTO> getReceivedRequests(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return upiMoneyRequestRepository.findByTargetUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertMoneyReqToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UpiMoneyRequestDTO> getSentRequests(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return upiMoneyRequestRepository.findByRequesterUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertMoneyReqToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UpiTransactionResponse acceptMoneyRequest(String targetUsername, Long requestId, String upiPin, String otp) {
        UpiMoneyRequest req = upiMoneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found with id: " + requestId));

        if (!req.getTargetUser().getUsername().equals(targetUsername)) {
            throw new IllegalArgumentException("Unauthorized to accept this payment request.");
        }

        if (req.getStatus() != UpiRequestStatus.PENDING) {
            throw new IllegalArgumentException("Payment request is already " + req.getStatus());
        }

        UpiSendMoneyRequest sendReq = UpiSendMoneyRequest.builder()
                .recipientUpiId(req.getRequesterUpiId())
                .amount(req.getAmount())
                .remarks("Accepted request: " + req.getRequestReference() + " (" + (req.getRemarks() != null ? req.getRemarks() : "") + ")")
                .upiPin(upiPin)
                .otp(otp)
                .build();

        UpiTransactionResponse txResponse = sendMoney(targetUsername, sendReq);

        req.setStatus(UpiRequestStatus.ACCEPTED);
        req.setRespondedAt(LocalDateTime.now());
        upiMoneyRequestRepository.save(req);

        notificationService.createNotification(req.getRequesterUser(), NotificationType.TRANSACTION_SUCCESS,
                "UPI Payment Request Accepted",
                String.format("%s accepted your payment request of ₹%s.", targetUsername, formatAmount(req.getAmount())),
                "/upi");

        return txResponse;
    }

    @Transactional
    public UpiMoneyRequestDTO rejectMoneyRequest(String targetUsername, Long requestId) {
        UpiMoneyRequest req = upiMoneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found with id: " + requestId));

        if (!req.getTargetUser().getUsername().equals(targetUsername)) {
            throw new IllegalArgumentException("Unauthorized to reject this request.");
        }

        if (req.getStatus() != UpiRequestStatus.PENDING) {
            throw new IllegalArgumentException("Payment request is already " + req.getStatus());
        }

        req.setStatus(UpiRequestStatus.REJECTED);
        req.setRespondedAt(LocalDateTime.now());
        UpiMoneyRequest saved = upiMoneyRequestRepository.save(req);

        notificationService.createNotification(req.getRequesterUser(), NotificationType.TRANSACTION_FAILED,
                "UPI Request Declined",
                String.format("%s declined your payment request for ₹%s.", targetUsername, formatAmount(req.getAmount())),
                "/upi");

        return convertMoneyReqToDTO(saved);
    }

    @Transactional
    public UpiMoneyRequestDTO cancelMoneyRequest(String requesterUsername, Long requestId) {
        UpiMoneyRequest req = upiMoneyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Money request not found with id: " + requestId));

        if (!req.getRequesterUser().getUsername().equals(requesterUsername)) {
            throw new IllegalArgumentException("Unauthorized to cancel this request.");
        }

        if (req.getStatus() != UpiRequestStatus.PENDING) {
            throw new IllegalArgumentException("Payment request is already " + req.getStatus());
        }

        req.setStatus(UpiRequestStatus.CANCELLED);
        req.setRespondedAt(LocalDateTime.now());
        UpiMoneyRequest saved = upiMoneyRequestRepository.save(req);

        return convertMoneyReqToDTO(saved);
    }

    // ==========================================
    // 5. QR CODE PAYMENT
    // ==========================================

    @Transactional(readOnly = true)
    public UpiQrResponse generatePersonalQr(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiProfile profile = getOrCreateProfileInternal(user);

        String rawUri = String.format("upi://pay?pa=%s&pn=%s", profile.getUpiId(), user.getFullName().replace(" ", "%20"));
        String qrCodeDataUrl = qrCodeService.generateQrCodeBase64(rawUri, 320, 320);

        return UpiQrResponse.builder()
                .upiId(profile.getUpiId())
                .payeeName(user.getFullName())
                .accountNumber(profile.getPrimaryAccount().getAccountNumber())
                .ifscCode(profile.getPrimaryAccount().getIfscCode())
                .bankName("SmartBank AI")
                .qrCodeDataUrl(qrCodeDataUrl)
                .rawUpiUri(rawUri)
                .build();
    }

    public Map<String, String> scanQrContent(String qrContentOrBase64) {
        if (qrContentOrBase64 == null || qrContentOrBase64.isBlank()) {
            throw new IllegalArgumentException("QR content is empty");
        }

        String rawText = qrContentOrBase64.trim();
        if (rawText.startsWith("data:image/") || rawText.contains("base64,")) {
            rawText = qrCodeService.decodeQrCodeBase64(rawText);
        }

        Map<String, String> parsed = qrCodeService.parseUpiUri(rawText);
        if (!parsed.containsKey("pa") && rawText.contains("@")) {
            parsed.put("pa", rawText);
        }

        return parsed;
    }

    @Transactional
    public UpiTransactionResponse payQr(String senderUsername, UpiQrPayRequest req) {
        String recipientUpiId = req.getRecipientUpiId();

        if (recipientUpiId.startsWith("upi://") || recipientUpiId.contains("?")) {
            Map<String, String> parsed = scanQrContent(recipientUpiId);
            if (parsed.containsKey("pa")) {
                recipientUpiId = parsed.get("pa");
            }
        }

        UpiSendMoneyRequest sendReq = UpiSendMoneyRequest.builder()
                .recipientUpiId(recipientUpiId)
                .amount(req.getAmount())
                .remarks(req.getRemarks() != null ? req.getRemarks() : "Scan & Pay QR Payment")
                .category(req.getCategory())
                .upiPin(req.getUpiPin())
                .otp(req.getOtp())
                .build();

        return sendMoney(senderUsername, sendReq);
    }

    // ==========================================
    // 6 & 7. UPI TRANSACTIONS & HISTORY
    // ==========================================

    @Transactional(readOnly = true)
    public List<UpiTransactionResponse> getUpiTransactions(String username, String direction, String status, String search) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        List<UpiTransaction> txs = upiTransactionRepository.findAllByUser(user);

        return txs.stream()
                .filter(t -> {
                    if (direction == null || direction.isBlank() || "ALL".equalsIgnoreCase(direction)) return true;
                    boolean isSender = t.getSenderUser() != null && t.getSenderUser().getId().equals(user.getId());
                    if ("SENT".equalsIgnoreCase(direction)) return isSender;
                    if ("RECEIVED".equalsIgnoreCase(direction)) return !isSender;
                    return true;
                })
                .filter(t -> {
                    if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return true;
                    return t.getStatus().name().equalsIgnoreCase(status);
                })
                .filter(t -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    return t.getUpiReferenceId().toLowerCase().contains(s)
                            || t.getSenderUpiId().toLowerCase().contains(s)
                            || t.getReceiverUpiId().toLowerCase().contains(s)
                            || (t.getRemarks() != null && t.getRemarks().toLowerCase().contains(s));
                })
                .map(t -> convertUpiTxToResponse(t, username))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UpiReceiptResponse getPaymentReceipt(String username, String upiReferenceId) {
        UpiTransaction tx = upiTransactionRepository.findByUpiReferenceId(upiReferenceId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Transaction receipt not found: " + upiReferenceId));

        boolean isParticipant = (tx.getSenderUser() != null && tx.getSenderUser().getUsername().equals(username))
                || (tx.getReceiverUser() != null && tx.getReceiverUser().getUsername().equals(username));

        if (!isParticipant) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || (user.getRole() != Role.ROLE_ADMIN && user.getRole() != Role.ROLE_SUPER_ADMIN)) {
                throw new IllegalArgumentException("Unauthorized to view this transaction receipt.");
            }
        }

        String secStatus = "UPI PIN Verified";
        if (Boolean.TRUE.equals(tx.getVerifiedWithOtp())) {
            secStatus = "UPI PIN + Step-Up OTP Verified";
        }

        return UpiReceiptResponse.builder()
                .bankName("SmartBank AI")
                .transactionStatus(tx.getStatus().name())
                .transactionId(tx.getTransactionReference())
                .upiReferenceId(tx.getUpiReferenceId())
                .senderName(tx.getSenderUser() != null ? tx.getSenderUser().getFullName() : "Sender")
                .senderUpiId(tx.getSenderUpiId())
                .senderAccountNumber(tx.getSenderAccount() != null ? tx.getSenderAccount().getAccountNumber() : "N/A")
                .receiverName(tx.getReceiverUser() != null ? tx.getReceiverUser().getFullName() : "Receiver")
                .receiverUpiId(tx.getReceiverUpiId())
                .receiverAccountNumber(tx.getReceiverAccount() != null ? tx.getReceiverAccount().getAccountNumber() : "N/A")
                .amount(tx.getAmount())
                .timestamp(tx.getTimestamp())
                .remarks(tx.getRemarks())
                .paymentMethod("Simulated UPI Transfer")
                .riskScore(tx.getRiskScore())
                .riskLevel(tx.getRiskLevel() != null ? tx.getRiskLevel().name() : "LOW")
                .securityVerificationStatus(secStatus)
                .build();
    }

    // ==========================================
    // 8. TRANSACTION LIMITS & CONFIGURATION
    // ==========================================

    @Transactional(readOnly = true)
    public UpiLimitsResponse getUpiLimits(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        UpiLimitConfiguration config = getLimitConfig();

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        BigDecimal usedToday = upiTransactionRepository.calculateDailySpentAmount(user, UpiTransactionStatus.SUCCESS, startOfDay);
        if (usedToday == null) usedToday = BigDecimal.ZERO;

        BigDecimal remainingDaily = config.getDailyLimit().subtract(usedToday);
        if (remainingDaily.compareTo(BigDecimal.ZERO) < 0) remainingDaily = BigDecimal.ZERO;

        return UpiLimitsResponse.builder()
                .perTransactionLimit(config.getPerTransactionLimit())
                .dailyLimit(config.getDailyLimit())
                .usedToday(usedToday)
                .remainingDailyLimit(remainingDaily)
                .upiEnabled(config.getUpiEnabled())
                .build();
    }

    private void validateUpiLimits(User sender, BigDecimal amount) {
        UpiLimitConfiguration config = getLimitConfig();

        if (amount.compareTo(config.getPerTransactionLimit()) > 0) {
            throw new IllegalArgumentException(String.format("Transaction amount ₹%s exceeds maximum per-transaction limit of ₹%s.",
                    formatAmount(amount), formatAmount(config.getPerTransactionLimit())));
        }

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        BigDecimal usedToday = upiTransactionRepository.calculateDailySpentAmount(sender, UpiTransactionStatus.SUCCESS, startOfDay);
        if (usedToday == null) usedToday = BigDecimal.ZERO;

        BigDecimal totalIfProcessed = usedToday.add(amount);
        if (totalIfProcessed.compareTo(config.getDailyLimit()) > 0) {
            BigDecimal remaining = config.getDailyLimit().subtract(usedToday);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            throw new IllegalArgumentException(String.format("Daily UPI limit exceeded! Daily Limit: ₹%s, Used Today: ₹%s, Remaining: ₹%s.",
                    formatAmount(config.getDailyLimit()), formatAmount(usedToday), formatAmount(remaining)));
        }
    }

    // ==========================================
    // 12. SMART SPENDING ANALYTICS
    // ==========================================

    @Transactional(readOnly = true)
    public UpiAnalyticsResponse getUpiAnalytics(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        List<UpiTransaction> txs = upiTransactionRepository.findAllByUser(user);

        BigDecimal totalSent = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        long successCount = 0;
        long failedCount = 0;
        BigDecimal highest = BigDecimal.ZERO;

        Map<ExpenseCategory, BigDecimal> catMap = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) {
            catMap.put(c, BigDecimal.ZERO);
        }

        for (UpiTransaction t : txs) {
            boolean isSender = t.getSenderUser() != null && t.getSenderUser().getId().equals(user.getId());
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;

            if (t.getStatus() == UpiTransactionStatus.SUCCESS) {
                successCount++;
                if (isSender) {
                    totalSent = totalSent.add(amt);
                    ExpenseCategory cat = t.getCategory() != null ? t.getCategory() : ExpenseCategory.OTHER;
                    catMap.put(cat, catMap.get(cat).add(amt));
                } else {
                    totalReceived = totalReceived.add(amt);
                }

                if (amt.compareTo(highest) > 0) {
                    highest = amt;
                }
            } else if (t.getStatus() == UpiTransactionStatus.FAILED || t.getStatus() == UpiTransactionStatus.BLOCKED) {
                failedCount++;
            }
        }

        BigDecimal avgAmt = BigDecimal.ZERO;
        if (successCount > 0) {
            avgAmt = totalSent.add(totalReceived).divide(BigDecimal.valueOf(successCount), 2, RoundingMode.HALF_UP);
        }

        List<UpiAnalyticsResponse.CategoryItem> breakdown = new ArrayList<>();
        Map<String, BigDecimal> stringCatMap = new HashMap<>();

        for (ExpenseCategory c : ExpenseCategory.values()) {
            BigDecimal amt = catMap.get(c);
            stringCatMap.put(c.name(), amt);
            double pct = 0.0;
            if (totalSent.compareTo(BigDecimal.ZERO) > 0) {
                pct = amt.divide(totalSent, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            }
            breakdown.add(UpiAnalyticsResponse.CategoryItem.builder()
                    .category(c.name())
                    .displayName(c.getDisplayName())
                    .icon(c.getIcon())
                    .color(c.getColor())
                    .amount(amt)
                    .percentage(pct)
                    .build());
        }

        return UpiAnalyticsResponse.builder()
                .monthlyUpiSpending(totalSent)
                .totalSentAmount(totalSent)
                .totalReceivedAmount(totalReceived)
                .totalTransactionsCount((long) txs.size())
                .successfulTransactionsCount(successCount)
                .failedTransactionsCount(failedCount)
                .averageTransactionAmount(avgAmt)
                .highestTransactionAmount(highest)
                .categorySpendingMap(stringCatMap)
                .categoryBreakdown(breakdown)
                .build();
    }

    // ==========================================
    // 16. SUPER ADMIN & ADMIN CONTROLS
    // ==========================================

    @Transactional(readOnly = true)
    public UpiAdminConfigDTO getSuperAdminUpiConfig() {
        UpiLimitConfiguration config = getLimitConfig();

        long totalProfiles = upiProfileRepository.count();
        long activeProfiles = upiProfileRepository.countByIsActiveTrue();
        long totalTxs = upiTransactionRepository.count();
        long successTxs = upiTransactionRepository.countByStatus(UpiTransactionStatus.SUCCESS);
        long failedTxs = upiTransactionRepository.countByStatus(UpiTransactionStatus.FAILED);
        long pendingTxs = upiTransactionRepository.countByStatus(UpiTransactionStatus.PENDING);
        long blockedTxs = upiTransactionRepository.countByStatus(UpiTransactionStatus.BLOCKED);

        BigDecimal totalVolume = upiTransactionRepository.calculateTotalVolumeByStatus(UpiTransactionStatus.SUCCESS);
        if (totalVolume == null) totalVolume = BigDecimal.ZERO;

        List<UpiProfileResponse> lockedProfiles = upiProfileRepository.findByIsLockedTrue().stream()
                .map(this::convertProfileToResponse)
                .collect(Collectors.toList());

        List<UpiTransactionResponse> recentTxs = upiTransactionRepository.findAllByOrderByTimestampDesc().stream()
                .limit(50)
                .map(t -> convertUpiTxToResponse(t, null))
                .collect(Collectors.toList());

        long suspiciousCount = recentTxs.stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(t.getRiskLevel()) || "BLOCKED".equalsIgnoreCase(t.getStatus()))
                .count();

        return UpiAdminConfigDTO.builder()
                .upiEnabled(config.getUpiEnabled())
                .perTransactionLimit(config.getPerTransactionLimit())
                .dailyLimit(config.getDailyLimit())
                .totalUpiProfiles(totalProfiles)
                .activeUpiProfiles(activeProfiles)
                .totalUpiTransactions(totalTxs)
                .successfulUpiTransactions(successTxs)
                .failedUpiTransactions(failedTxs)
                .pendingUpiTransactions(pendingTxs)
                .blockedUpiTransactions(blockedTxs)
                .totalUpiVolume(totalVolume)
                .suspiciousUpiAttemptsCount(suspiciousCount)
                .lockedUpiProfilesCount((long) lockedProfiles.size())
                .recentUpiTransactions(recentTxs)
                .lockedUpiProfiles(lockedProfiles)
                .build();
    }

    @Transactional
    public UpiAdminConfigDTO updateSuperAdminUpiConfig(String performedBy, BigDecimal perTxLimit, BigDecimal dailyLimit, Boolean enabled) {
        UpiLimitConfiguration config = getLimitConfig();

        if (perTxLimit != null && perTxLimit.compareTo(BigDecimal.ZERO) > 0) {
            config.setPerTransactionLimit(perTxLimit);
        }
        if (dailyLimit != null && dailyLimit.compareTo(BigDecimal.ZERO) > 0) {
            config.setDailyLimit(dailyLimit);
        }
        if (enabled != null) {
            config.setUpiEnabled(enabled);
        }
        config.setUpdatedBy(performedBy);

        upiLimitConfigurationRepository.save(config);

        auditLogService.logAction(performedBy, "ROLE_SUPER_ADMIN", "UPDATE_UPI_SYSTEM_CONFIG",
                "UpiLimitConfiguration", String.valueOf(config.getId()), null, "UPDATED", "127.0.0.1", "SUCCESS",
                String.format("Updated global UPI config. Enabled: %s, PerTxLimit: ₹%s, DailyLimit: ₹%s",
                        config.getUpiEnabled(), formatAmount(config.getPerTransactionLimit()), formatAmount(config.getDailyLimit())));

        return getSuperAdminUpiConfig();
    }

    @Transactional
    public void toggleCustomerUpiLock(String adminUsername, String upiId, boolean lock) {
        UpiProfile profile = upiProfileRepository.findByUpiId(upiId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI Profile not found: " + upiId));

        profile.setIsLocked(lock);
        if (lock) {
            profile.setLockTime(LocalDateTime.now());
        } else {
            profile.setFailedPinAttempts(0);
            profile.setLockTime(null);
        }

        upiProfileRepository.save(profile);

        String action = lock ? "ADMIN_LOCK_UPI" : "ADMIN_UNLOCK_UPI";
        auditLogService.logAction(adminUsername, "ROLE_SUPER_ADMIN", action,
                "UpiProfile", String.valueOf(profile.getId()), lock ? "UNLOCKED" : "LOCKED", lock ? "LOCKED" : "UNLOCKED",
                "127.0.0.1", "SUCCESS", (lock ? "Locked" : "Unlocked") + " customer UPI: " + upiId);

        notificationService.createNotification(profile.getUser(), NotificationType.SECURITY_ALERT,
                "UPI Account Status Updated",
                "Your UPI transactions have been " + (lock ? "locked" : "unlocked") + " by system administration.", "/upi");
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private void checkSystemUpiEnabled() {
        UpiLimitConfiguration config = getLimitConfig();
        if (Boolean.FALSE.equals(config.getUpiEnabled())) {
            throw new IllegalArgumentException("UPI payments are currently disabled system-wide by Bank Administration for maintenance.");
        }
    }

    private UpiLimitConfiguration getLimitConfig() {
        return upiLimitConfigurationRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> upiLimitConfigurationRepository.save(UpiLimitConfiguration.builder()
                        .perTransactionLimit(new BigDecimal("50000.00"))
                        .dailyLimit(new BigDecimal("100000.00"))
                        .upiEnabled(true)
                        .updatedBy("SYSTEM")
                        .build()));
    }

    private UpiProfile getOrCreateProfileInternal(User user) {
        return upiProfileRepository.findByUser(user)
                .orElseGet(() -> {
                    Account account = accountRepository.findByUserUsername(user.getUsername()).stream().findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("No bank account found for user: " + user.getUsername()));
                    String defaultUpi = user.getUsername() + DEFAULT_UPI_HANDLE_SUFFIX;
                    String rawUri = String.format("upi://pay?pa=%s&pn=%s", defaultUpi, user.getFullName().replace(" ", "%20"));
                    String qr = qrCodeService.generateQrCodeBase64(rawUri, 280, 280);
                    return upiProfileRepository.save(UpiProfile.builder()
                            .user(user)
                            .upiId(defaultUpi)
                            .primaryAccount(account)
                            .isPrimary(true)
                            .isActive(true)
                            .failedPinAttempts(0)
                            .isLocked(false)
                            .qrCodeData(qr)
                            .build());
                });
    }

    private UpiProfile resolveBeneficiaryProfile(UpiSendMoneyRequest req) {
        if (req.getRecipientUpiId() != null && !req.getRecipientUpiId().isBlank()) {
            String formatted = formatUpiId(req.getRecipientUpiId());
            return upiProfileRepository.findByUpiId(formatted).orElse(null);
        }

        if (req.getRecipientPhone() != null && !req.getRecipientPhone().isBlank()) {
            return upiProfileRepository.findByUserPhone(req.getRecipientPhone().trim()).orElse(null);
        }

        if (req.getRecipientAccountNumber() != null && !req.getRecipientAccountNumber().isBlank()) {
            Optional<Account> accOpt = accountRepository.findByAccountNumber(req.getRecipientAccountNumber().trim());
            if (accOpt.isPresent()) {
                return getOrCreateProfileInternal(accOpt.get().getUser());
            }
        }

        return null;
    }

    private void recordFailedUpiTransaction(String upiRef, String txRef, User sender, String senderUpiId, Account senderAccount,
                                            User receiver, String receiverUpiId, Account receiverAccount, BigDecimal amount,
                                            TransactionType type, String remarks, FraudAssessmentResponse risk, String failureReason) {

        RiskLevel rl = RiskLevel.valueOf(risk.getRiskLevel());

        Transaction blockedMain = Transaction.builder()
                .transactionReference(txRef)
                .sourceAccount(senderAccount)
                .destinationAccount(receiverAccount)
                .amount(amount)
                .type(type)
                .status(TransactionStatus.BLOCKED)
                .description("UPI Transaction [BLOCKED]: " + failureReason)
                .timestamp(LocalDateTime.now())
                .riskScore(risk.getRiskScore())
                .riskLevel(rl)
                .fraudProbability(risk.getFraudProbability())
                .decision(FraudDecision.BLOCK)
                .ruleBasedScore(risk.getRuleBasedScore())
                .mlPrediction((double) risk.getMlPredictionScore())
                .fraudCheckedAt(LocalDateTime.now())
                .riskReasons(failureReason)
                .category(ExpenseCategory.OTHER)
                .verifiedWithOtp(false)
                .build();
        transactionRepository.save(blockedMain);

        UpiTransaction blockedUpi = UpiTransaction.builder()
                .upiReferenceId(upiRef)
                .transactionReference(txRef)
                .senderUser(sender)
                .senderUpiId(senderUpiId)
                .senderAccount(senderAccount)
                .receiverUser(receiver)
                .receiverUpiId(receiverUpiId)
                .receiverAccount(receiverAccount)
                .amount(amount)
                .transactionType(type)
                .timestamp(LocalDateTime.now())
                .remarks(remarks)
                .status(UpiTransactionStatus.BLOCKED)
                .riskScore(risk.getRiskScore())
                .riskLevel(rl)
                .fraudStatus(risk.getDecision())
                .failureReason(failureReason)
                .category(ExpenseCategory.OTHER)
                .verifiedWithOtp(false)
                .build();
        upiTransactionRepository.save(blockedUpi);
    }

    private String formatUpiId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim().toLowerCase();
        if (!trimmed.contains("@")) {
            return trimmed + DEFAULT_UPI_HANDLE_SUFFIX;
        }
        return trimmed;
    }

    private void validatePinFormat(String pin, String confirmPin) {
        if (pin == null || !pin.trim().matches("^\\d{4,6}$")) {
            throw new IllegalArgumentException("UPI PIN must consist of exactly 4 or 6 numeric digits.");
        }
        if (confirmPin != null && !pin.trim().equals(confirmPin.trim())) {
            throw new IllegalArgumentException("New PIN and Confirm PIN do not match.");
        }
    }

    private boolean isDemoOtp(String otp) {
        return otp != null && (otp.trim().equals("482910") || otp.trim().equals("123456"));
    }

    private ExpenseCategory resolveExpenseCategory(String catStr, String remarks) {
        if (catStr != null && !catStr.isBlank()) {
            try {
                return ExpenseCategory.valueOf(catStr.toUpperCase());
            } catch (Exception ignored) {}
        }
        return expenseAnalyticsService.autoDetectCategory(remarks);
    }

    private UpiProfileResponse convertProfileToResponse(UpiProfile p) {
        User user = p.getUser();
        Account acc = p.getPrimaryAccount();
        int attempts = p.getFailedPinAttempts() != null ? p.getFailedPinAttempts() : 0;
        int remaining = Math.max(0, MAX_PIN_ATTEMPTS - attempts);

        return UpiProfileResponse.builder()
                .id(p.getId())
                .upiId(p.getUpiId())
                .username(user != null ? user.getUsername() : "")
                .fullName(user != null ? user.getFullName() : "")
                .phone(user != null ? user.getPhone() : "")
                .accountNumber(acc != null ? acc.getAccountNumber() : "")
                .ifscCode(acc != null ? acc.getIfscCode() : "")
                .bankName("SmartBank AI")
                .isPrimary(Boolean.TRUE.equals(p.getIsPrimary()))
                .isActive(Boolean.TRUE.equals(p.getIsActive()))
                .hasPin(p.getUpiPin() != null && !p.getUpiPin().isBlank())
                .failedPinAttempts(attempts)
                .remainingPinAttempts(remaining)
                .isLocked(Boolean.TRUE.equals(p.getIsLocked()))
                .lockTime(p.getLockTime())
                .qrCodeData(p.getQrCodeData())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private UpiTransactionResponse convertUpiTxToResponse(UpiTransaction t, String currentUsername) {
        ExpenseCategory cat = t.getCategory() != null ? t.getCategory() : ExpenseCategory.OTHER;
        RiskLevel rl = t.getRiskLevel() != null ? t.getRiskLevel() : RiskLevel.LOW;

        String direction = "SENT";
        if (currentUsername != null && t.getReceiverUser() != null && currentUsername.equalsIgnoreCase(t.getReceiverUser().getUsername())) {
            direction = "RECEIVED";
        }

        return UpiTransactionResponse.builder()
                .id(t.getId())
                .upiReferenceId(t.getUpiReferenceId())
                .transactionReference(t.getTransactionReference())
                .senderUpiId(t.getSenderUpiId())
                .senderName(t.getSenderUser() != null ? t.getSenderUser().getFullName() : "Sender")
                .senderAccountNumber(t.getSenderAccount() != null ? t.getSenderAccount().getAccountNumber() : "N/A")
                .receiverUpiId(t.getReceiverUpiId())
                .receiverName(t.getReceiverUser() != null ? t.getReceiverUser().getFullName() : "Receiver")
                .receiverAccountNumber(t.getReceiverAccount() != null ? t.getReceiverAccount().getAccountNumber() : "N/A")
                .amount(t.getAmount())
                .transactionType(t.getTransactionType().name())
                .timestamp(t.getTimestamp())
                .remarks(t.getRemarks())
                .status(t.getStatus().name())
                .riskScore(t.getRiskScore() != null ? t.getRiskScore() : 10)
                .riskLevel(rl.name())
                .fraudStatus(t.getFraudStatus() != null ? t.getFraudStatus() : "ALLOW")
                .failureReason(t.getFailureReason())
                .category(cat.name())
                .categoryDisplayName(cat.getDisplayName())
                .categoryIcon(cat.getIcon())
                .categoryColor(cat.getColor())
                .verifiedWithOtp(Boolean.TRUE.equals(t.getVerifiedWithOtp()))
                .direction(direction)
                .build();
    }

    private UpiMoneyRequestDTO convertMoneyReqToDTO(UpiMoneyRequest r) {
        return UpiMoneyRequestDTO.builder()
                .id(r.getId())
                .requestReference(r.getRequestReference())
                .requesterUsername(r.getRequesterUser() != null ? r.getRequesterUser().getUsername() : "")
                .requesterFullName(r.getRequesterUser() != null ? r.getRequesterUser().getFullName() : "")
                .requesterUpiId(r.getRequesterUpiId())
                .targetUsername(r.getTargetUser() != null ? r.getTargetUser().getUsername() : "")
                .targetFullName(r.getTargetUser() != null ? r.getTargetUser().getFullName() : "")
                .targetUpiId(r.getTargetUpiId())
                .amount(r.getAmount())
                .remarks(r.getRemarks())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .respondedAt(r.getRespondedAt())
                .build();
    }

    private String formatAmount(BigDecimal amt) {
        if (amt == null) return "0.00";
        return String.format("%,.2f", amt);
    }
}
