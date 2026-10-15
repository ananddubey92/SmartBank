package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FinancialHealthResponse;
import com.project.onlinebankingsystem.dto.SpendingPredictionResponse;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.model.Account;
import com.project.onlinebankingsystem.model.Transaction;
import com.project.onlinebankingsystem.model.User;
import com.project.onlinebankingsystem.repository.AccountRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialHealthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private FinancialHealthService financialHealthService;

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
                .balance(new BigDecimal("125000.00"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();
    }

    @Test
    @DisplayName("Calculate Financial Health for active user with healthy income and savings")
    void testCalculateFinancialHealth_HealthyUser() {
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        List<Transaction> transactions = new ArrayList<>();
        // Ensure timestamps are within current month regardless of day of month
        LocalDateTime currentMonthTxTime = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay().plusHours(10);

        // Income this month: 65,000
        transactions.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("65000.00"))
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .timestamp(currentMonthTxTime)
                .category(ExpenseCategory.OTHER)
                .build());

        // Spending this month: 15,000
        transactions.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("5000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(currentMonthTxTime.plusHours(1))
                .category(ExpenseCategory.FOOD)
                .build());
        transactions.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("10000.00"))
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .timestamp(currentMonthTxTime.plusHours(2))
                .category(ExpenseCategory.SHOPPING)
                .build());

        when(transactionRepository.findAllByAccounts(List.of(account))).thenReturn(transactions);

        FinancialHealthResponse health = financialHealthService.calculateFinancialHealth("rahul");

        assertNotNull(health);
        assertNotNull(health.getFinancialHealthScore());
        assertTrue(health.getFinancialHealthScore() >= 0 && health.getFinancialHealthScore() <= 100);
        assertEquals(new BigDecimal("65000.00"), health.getCurrentMonthIncome());
        assertEquals(new BigDecimal("15000.00"), health.getCurrentMonthSpending());
        assertEquals(new BigDecimal("50000.00"), health.getCurrentMonthSavings());
        assertTrue(health.getSavingsRate() > 0);
        assertFalse(health.getIntelligentInsights().isEmpty());
    }

    @Test
    @DisplayName("Predict Month-End Spending based on current daily spending velocity")
    void testPredictSpending() {
        when(accountRepository.findByUserUsername("rahul")).thenReturn(List.of(account));

        List<Transaction> transactions = new ArrayList<>();
        LocalDateTime currentMonthTxTime = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay().plusHours(10);

        transactions.add(Transaction.builder()
                .sourceAccount(account)
                .amount(new BigDecimal("6000.00"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .timestamp(currentMonthTxTime)
                .category(ExpenseCategory.FOOD)
                .build());

        when(transactionRepository.findAllByAccounts(List.of(account))).thenReturn(transactions);

        SpendingPredictionResponse prediction = financialHealthService.predictSpending("rahul");

        assertNotNull(prediction);
        assertNotNull(prediction.getPredictedMonthEndSpending());
        assertTrue(prediction.getPredictedMonthEndSpending().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(prediction.getPredictionSummary());
    }
}
