package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.*;
import com.project.onlinebankingsystem.enums.*;
import com.project.onlinebankingsystem.exception.InsufficientBalanceException;
import com.project.onlinebankingsystem.model.*;
import com.project.onlinebankingsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpiServiceTest {

    @Mock private UpiProfileRepository upiProfileRepository;
    @Mock private UpiMoneyRequestRepository upiMoneyRequestRepository;
    @Mock private UpiLimitConfigurationRepository upiLimitConfigurationRepository;
    @Mock private UpiTransactionRepository upiTransactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private FraudDetectionService fraudDetectionService;
    @Mock private ExpenseAnalyticsService expenseAnalyticsService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private OtpService otpService;
    @Mock private QrCodeService qrCodeService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UpiService upiService;

    private User senderUser;
    private User receiverUser;
    private Account senderAccount;
    private Account receiverAccount;
    private UpiProfile senderProfile;
    private UpiProfile receiverProfile;
    private UpiLimitConfiguration limitConfig;

    @BeforeEach
    void setUp() {
        senderUser = User.builder().id(1L).username("rahul").fullName("Rahul Sharma").phone("9876543210").role(Role.ROLE_CUSTOMER).password("encodedPwd").build();
        receiverUser = User.builder().id(2L).username("priya").fullName("Priya Patel").phone("9123456789").role(Role.ROLE_CUSTOMER).password("encodedPwd").build();

        senderAccount = Account.builder().id(10L).accountNumber("1002345678").ifscCode("SMART000101").balance(new BigDecimal("50000.00")).status(AccountStatus.ACTIVE).user(senderUser).build();
        receiverAccount = Account.builder().id(20L).accountNumber("2009876543").ifscCode("SMART000101").balance(new BigDecimal("10000.00")).status(AccountStatus.ACTIVE).user(receiverUser).build();

        senderProfile = UpiProfile.builder().id(100L).user(senderUser).upiId("rahul@smartbank").primaryAccount(senderAccount).isPrimary(true).isActive(true).upiPin("hashedPin").failedPinAttempts(0).isLocked(false).build();
        receiverProfile = UpiProfile.builder().id(200L).user(receiverUser).upiId("priya@smartbank").primaryAccount(receiverAccount).isPrimary(true).isActive(true).upiPin("hashedPin").failedPinAttempts(0).isLocked(false).build();

        limitConfig = UpiLimitConfiguration.builder().id(1L).perTransactionLimit(new BigDecimal("50000.00")).dailyLimit(new BigDecimal("100000.00")).upiEnabled(true).build();
    }

    @Test
    @DisplayName("Create UPI ID successfully for valid user and account")
    void testCreateUpiId_Success() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(senderAccount));
        when(upiProfileRepository.existsByUpiId("newhandle@smartbank")).thenReturn(false);
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.empty());
        when(qrCodeService.generateQrCodeBase64(any(), anyInt(), anyInt())).thenReturn("data:image/png;base64,demo");
        when(upiProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpiProfileResponse resp = upiService.createUpiId("rahul", "newhandle@smartbank", "1002345678");

        assertNotNull(resp);
        assertEquals("newhandle@smartbank", resp.getUpiId());
        assertTrue(resp.getIsActive());
        verify(notificationService).createNotification(eq(senderUser), eq(NotificationType.SECURITY_ALERT), any(), any(), any());
    }

    @Test
    @DisplayName("Prevent duplicate UPI ID creation")
    void testCreateUpiId_DuplicatePrevented() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(senderAccount));
        when(upiProfileRepository.existsByUpiId("rahul@smartbank")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> upiService.createUpiId("rahul", "rahul@smartbank", "1002345678"));
    }

    @Test
    @DisplayName("Set UPI PIN successfully")
    void testSetUpiPin_Success() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(passwordEncoder.matches("password123", "encodedPwd")).thenReturn(true);
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.encode("123456")).thenReturn("hashed123456");

        assertDoesNotThrow(() -> upiService.setUpiPin("rahul", "123456", "123456", "password123"));
        verify(upiProfileRepository).save(any());
    }

    @Test
    @DisplayName("Lock UPI account after 5 consecutive incorrect PIN attempts")
    void testVerifyUpiPin_LockAfter5FailedAttempts() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        senderProfile.setFailedPinAttempts(4); // 5th attempt is failing
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.matches("999999", "hashedPin")).thenReturn(false);

        Exception ex = assertThrows(IllegalArgumentException.class, () -> upiService.verifyUpiPin("rahul", "999999"));
        assertTrue(ex.getMessage().contains("Maximum 5 attempts reached"));
        assertTrue(senderProfile.getIsLocked());
    }

    @Test
    @DisplayName("Successful atomic UPI Money Transfer")
    void testSendMoney_Success() {
        when(upiLimitConfigurationRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(limitConfig));
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.matches("123456", "hashedPin")).thenReturn(true);
        when(upiProfileRepository.findByUpiId("priya@smartbank")).thenReturn(Optional.of(receiverProfile));

        FraudAssessmentResponse fraudResp = FraudAssessmentResponse.builder()
                .riskScore(12).riskLevel("LOW").fraudProbability(12.0).decision("ALLOW").ruleBasedScore(12).mlPredictionScore(12)
                .reasons(List.of("Clean baseline")).requiresOtp(false).build();
        when(fraudDetectionService.assessRisk(any(), any(), any())).thenReturn(fraudResp);
        when(expenseAnalyticsService.autoDetectCategory(any())).thenReturn(ExpenseCategory.OTHER);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(upiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpiSendMoneyRequest req = UpiSendMoneyRequest.builder()
                .recipientUpiId("priya@smartbank").amount(new BigDecimal("1000.00")).remarks("Test Payment").upiPin("123456").build();

        UpiTransactionResponse resp = upiService.sendMoney("rahul", req);

        assertNotNull(resp);
        assertEquals("SUCCESS", resp.getStatus());
        assertEquals(new BigDecimal("49000.00"), senderAccount.getBalance());
        assertEquals(new BigDecimal("11000.00"), receiverAccount.getBalance());
        verify(accountRepository).save(senderAccount);
        verify(accountRepository).save(receiverAccount);
    }

    @Test
    @DisplayName("Fail UPI transfer when sender has insufficient balance")
    void testSendMoney_InsufficientBalance() {
        when(upiLimitConfigurationRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(limitConfig));
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.matches("123456", "hashedPin")).thenReturn(true);
        when(upiProfileRepository.findByUpiId("priya@smartbank")).thenReturn(Optional.of(receiverProfile));

        UpiSendMoneyRequest req = UpiSendMoneyRequest.builder()
                .recipientUpiId("priya@smartbank").amount(new BigDecimal("999999.00")).remarks("Big Payment").upiPin("123456").build();

        assertThrows(InsufficientBalanceException.class, () -> upiService.sendMoney("rahul", req));
    }

    @Test
    @DisplayName("Fail UPI transfer when per-transaction limit exceeded")
    void testSendMoney_LimitExceeded() {
        when(upiLimitConfigurationRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(limitConfig));
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.matches("123456", "hashedPin")).thenReturn(true);
        when(upiProfileRepository.findByUpiId("priya@smartbank")).thenReturn(Optional.of(receiverProfile));

        senderAccount.setBalance(new BigDecimal("100000.00"));
        UpiSendMoneyRequest req = UpiSendMoneyRequest.builder()
                .recipientUpiId("priya@smartbank").amount(new BigDecimal("60000.00")).remarks("Over limit").upiPin("123456").build();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> upiService.sendMoney("rahul", req));
        assertTrue(ex.getMessage().contains("per-transaction limit"));
    }

    @Test
    @DisplayName("Require OTP for high-risk suspicious UPI payment")
    void testSendMoney_HighRiskRequiresOtp() {
        when(upiLimitConfigurationRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(limitConfig));
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(senderUser));
        when(upiProfileRepository.findByUser(senderUser)).thenReturn(Optional.of(senderProfile));
        when(passwordEncoder.matches("123456", "hashedPin")).thenReturn(true);
        when(upiProfileRepository.findByUpiId("priya@smartbank")).thenReturn(Optional.of(receiverProfile));

        FraudAssessmentResponse highRisk = FraudAssessmentResponse.builder()
                .riskScore(85).riskLevel("HIGH").fraudProbability(85.0).decision("REVIEW").ruleBasedScore(85).mlPredictionScore(85)
                .reasons(List.of("Velocity spike")).requiresOtp(true).build();
        when(fraudDetectionService.assessRisk(any(), any(), any())).thenReturn(highRisk);
        when(otpService.validateOtp(any(), any())).thenReturn(false); // Invalid OTP

        UpiSendMoneyRequest req = UpiSendMoneyRequest.builder()
                .recipientUpiId("priya@smartbank").amount(new BigDecimal("40000.00")).remarks("High risk payment").upiPin("123456").otp("000000").build();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> upiService.sendMoney("rahul", req));
        assertTrue(ex.getMessage().contains("OTP_REQUIRED"));
    }
}
