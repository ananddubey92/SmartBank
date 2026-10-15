package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.AiAssistantRequest;
import com.project.onlinebankingsystem.dto.AiAssistantResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import com.project.onlinebankingsystem.repository.UserRepository;
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
class AiBankingAssistantServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinancialHealthService financialHealthService;

    @InjectMocks
    private AiBankingAssistantService aiBankingAssistantService;

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("rahul")
                .fullName("Rahul Sharma")
                .build();

        account = Account.builder()
                .id(10L)
                .accountNumber("1002345678")
                .accountType(AccountType.SAVING)
                .balance(new BigDecimal("125000.50"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        org.mockito.Mockito.lenient().when(financialHealthService.calculateFinancialHealth(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(com.project.onlinebankingsystem.dto.FinancialHealthResponse.builder()
                        .currentMonthSpending(new BigDecimal("4500.00"))
                        .currentMonthIncome(new BigDecimal("30000.00"))
                        .currentMonthSavings(new BigDecimal("25500.00"))
                        .savingsRate(85.0)
                        .financialHealthScore(85)
                        .healthGrade("Excellent")
                        .savingsScore(88)
                        .spendingScore(82)
                        .transactionRiskScore(90)
                        .categoryDistribution(List.of())
                        .intelligentInsights(List.of("Your savings rate is healthy."))
                        .build());
    }

    @Test
    @DisplayName("Assistant answers Balance query accurately")
    void testQuery_BalanceIntent() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        AiAssistantResponse response = aiBankingAssistantService.processQuery("rahul", "What is my current account balance?");

        assertNotNull(response);
        assertEquals("BALANCE_INQUIRY", response.getIntent());
        assertTrue(response.getAnswer().contains("1002345678"));
        assertTrue(response.getAnswer().contains("125,000.50") || response.getAnswer().contains("125000"));
        assertNotNull(response.getActionUrl());
    }

    @Test
    @DisplayName("Assistant answers Spending inquiry accurately")
    void testQuery_SpendingIntent() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        List<Transaction> txs = new ArrayList<>();
        txs.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("4500.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(LocalDateTime.now().minusDays(2))
                .category(ExpenseCategory.FOOD)
                .build());

        when(transactionRepository.findAllByAccount(account)).thenReturn(txs);

        AiAssistantResponse response = aiBankingAssistantService.processQuery("rahul", "How much have I spent this month?");

        assertNotNull(response);
        assertEquals("SPENDING_SUMMARY", response.getIntent());
        assertTrue(response.getAnswer().contains("4,500.00") || response.getAnswer().contains("4500"));
    }

    @Test
    @DisplayName("Assistant evaluates Affordability check")
    void testQuery_AffordabilityIntent() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        AiAssistantResponse response = aiBankingAssistantService.processQuery("rahul", "Can I afford to spend 25000 on a vacation?");

        assertNotNull(response);
        assertEquals("AFFORDABILITY_CHECK", response.getIntent());
        assertTrue(response.getAnswer().toLowerCase().contains("afford") || response.getAnswer().toLowerCase().contains("balance"));
    }

    @Test
    @DisplayName("Assistant explains blocked transaction reason")
    void testQuery_BlockedReasonIntent() {
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        List<Transaction> txs = new ArrayList<>();
        txs.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("85000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.BLOCKED)
                .riskLevel(RiskLevel.HIGH)
                .riskScore(89)
                .riskReasons("Unusually high amount (4.5x average); Late-night timing.")
                .timestamp(LocalDateTime.now().minusHours(5))
                .build());

        when(transactionRepository.findAllByAccount(account)).thenReturn(txs);

        AiAssistantResponse response = aiBankingAssistantService.processQuery("rahul", "Why was my last transfer blocked?");

        assertNotNull(response);
        assertEquals("BLOCKED_REASON", response.getIntent());
        assertTrue(response.getAnswer().contains("85,000") || response.getAnswer().contains("Late-night") || response.getAnswer().contains("Blocked"));
    }
}
