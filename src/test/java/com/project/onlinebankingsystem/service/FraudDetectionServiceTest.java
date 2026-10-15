package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FraudAssessmentResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Beneficiary;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.BeneficiaryRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @Mock
    private MlFraudServiceClient mlFraudServiceClient;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private User testUser;
    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(mlFraudServiceClient.predict(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenAnswer(inv -> {
            BigDecimal amt = inv.getArgument(0);
            boolean isNew = inv.getArgument(4);
            double prob = (amt != null && amt.doubleValue() >= 40000 || isNew) ? 82.0 : 10.0;
            java.util.Map<String, Object> res = new java.util.HashMap<>();
            res.put("fraud_probability", prob);
            res.put("is_fraud", prob >= 60.0);
            res.put("risk_score", (int) Math.round(prob));
            res.put("risk_level", prob >= 81.0 ? "CRITICAL" : (prob >= 61.0 ? "HIGH" : (prob >= 31.0 ? "MEDIUM" : "LOW")));
            res.put("decision", prob >= 81.0 ? "BLOCK" : (prob >= 61.0 ? "REVIEW" : "ALLOW"));
            return res;
        });

        testUser = User.builder()
                .id(1L)
                .username("rahul")
                .accountSecurityStatus("GOOD")
                .failedLoginAttempts(0)
                .build();

        sourceAccount = Account.builder()
                .id(10L)
                .accountNumber("1002345678")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .user(testUser)
                .build();

        destinationAccount = Account.builder()
                .id(20L)
                .accountNumber("1002345679")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("10000.00"))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Evaluate normal transfer: should produce LOW risk score (<= 30) and require no OTP")
    void testAssessRisk_NormalTransfer_LowRisk() {
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(sourceAccount));

        // Established beneficiary with prior transfers
        Beneficiary establishedBen = Beneficiary.builder()
                .id(1L)
                .user(testUser)
                .accountNumber("1002345679")
                .name("Priya Verma")
                .createdAt(LocalDateTime.now().minusDays(30))
                .transferCount(5)
                .build();
        when(beneficiaryRepository.findByUserUsername("rahul"))
                .thenReturn(List.of(establishedBen));

        // Historical successful transfers establishing average around 2,000
        List<Transaction> past = new ArrayList<>();
        past.add(Transaction.builder()
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .amount(new BigDecimal("2000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(LocalDateTime.now().minusDays(3))
                .build());

        when(transactionRepository.findAllByAccount(sourceAccount)).thenReturn(past);

        FraudAssessmentResponse response = fraudDetectionService.assessRisk(
                "1002345678", "1002345679", new BigDecimal("1500.00")
        );

        assertNotNull(response);
        assertTrue(response.getRiskScore() <= 30, "Score should be <= 30 for low risk, but was " + response.getRiskScore());
        assertEquals(RiskLevel.LOW.name(), response.getRiskLevel());
        assertFalse(response.isRequiresOtp());
    }

    @Test
    @DisplayName("Evaluate high-risk transfer: new beneficiary, velocity spike, high balance depletion triggers HIGH risk (>= 71) and OTP")
    void testAssessRisk_HighRiskTransfer_RequiresOtp() {
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(sourceAccount));

        // Beneficiary registered just 1 hour ago (within 24 hours novelty)
        Beneficiary newBen = Beneficiary.builder()
                .id(2L)
                .user(testUser)
                .accountNumber("1002345679")
                .name("Unknown Recipient")
                .createdAt(LocalDateTime.now().minusHours(1))
                .transferCount(0)
                .build();
        when(beneficiaryRepository.findByUserUsername("rahul"))
                .thenReturn(List.of(newBen));

        // Velocity spike: 2 rapid transfers in last 10 minutes
        List<Transaction> past = new ArrayList<>();
        past.add(Transaction.builder()
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .amount(new BigDecimal("1000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(LocalDateTime.now().minusMinutes(2))
                .build());
        past.add(Transaction.builder()
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .amount(new BigDecimal("1000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(LocalDateTime.now().minusMinutes(5))
                .build());

        when(transactionRepository.findAllByAccount(sourceAccount)).thenReturn(past);

        // Draining 48,000 out of 50,000 balance (> 85% depletion)
        FraudAssessmentResponse response = fraudDetectionService.assessRisk(
                "1002345678", "1002345679", new BigDecimal("48000.00")
        );

        assertNotNull(response);
        assertTrue(response.getRiskScore() >= 71, "Score should be >= 71 for high risk, but was " + response.getRiskScore());
        assertTrue(response.getRiskLevel().equals(RiskLevel.HIGH.name()) || response.getRiskLevel().equals(RiskLevel.CRITICAL.name()));
        assertTrue(response.isRequiresOtp(), "High risk transfer must require OTP step-up verification");
        assertFalse(response.getReasons().isEmpty());
    }

    @Test
    @DisplayName("Verify exact amount handling for ₹4000: transfer amount must remain exactly 4000 and not derived from balance")
    void testAssessRisk_ExactAmount_4000() {
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(sourceAccount));
        when(beneficiaryRepository.findByUserUsername("rahul")).thenReturn(List.of());
        when(transactionRepository.findAllByAccount(sourceAccount)).thenReturn(List.of());

        BigDecimal inputAmount = new BigDecimal("4000.00");
        FraudAssessmentResponse response = fraudDetectionService.assessRisk(
                "1002345678", "1002345679", inputAmount
        );

        assertNotNull(response);
        assertEquals(inputAmount, response.getTransferAmount(), "Transfer amount in response must match exactly ₹4,000.00 without alteration");
        assertNotEquals(sourceAccount.getBalance(), response.getTransferAmount(), "Transfer amount must not be replaced by account balance");
        assertNotNull(response.getRiskScore(), "Risk score must be calculated");
    }

    @Test
    @DisplayName("Verify exact amount handling for ₹5000: transfer amount must remain exactly 5000")
    void testAssessRisk_ExactAmount_5000() {
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(sourceAccount));
        when(beneficiaryRepository.findByUserUsername("rahul")).thenReturn(List.of());
        when(transactionRepository.findAllByAccount(sourceAccount)).thenReturn(List.of());

        BigDecimal inputAmount = new BigDecimal("5000.00");
        FraudAssessmentResponse response = fraudDetectionService.assessRisk(
                "1002345678", "1002345679", inputAmount
        );

        assertNotNull(response);
        assertEquals(inputAmount, response.getTransferAmount(), "Transfer amount in response must match exactly ₹5,000.00");
    }

    @Test
    @DisplayName("Verify exact amount handling for ₹10000: transfer amount must remain exactly 10000")
    void testAssessRisk_ExactAmount_10000() {
        when(accountRepository.findByAccountNumber("1002345678")).thenReturn(Optional.of(sourceAccount));
        when(beneficiaryRepository.findByUserUsername("rahul")).thenReturn(List.of());
        when(transactionRepository.findAllByAccount(sourceAccount)).thenReturn(List.of());

        BigDecimal inputAmount = new BigDecimal("10000.00");
        FraudAssessmentResponse response = fraudDetectionService.assessRisk(
                "1002345678", "1002345679", inputAmount
        );

        assertNotNull(response);
        assertEquals(inputAmount, response.getTransferAmount(), "Transfer amount in response must match exactly ₹10,000.00");
    }
}
