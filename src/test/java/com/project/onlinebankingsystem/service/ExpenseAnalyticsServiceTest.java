package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.ExpenseAnalyticsResponse;
import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.repository.AccountRepository;
import com.project.onlinebankingsystem.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseAnalyticsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ExpenseAnalyticsService expenseAnalyticsService;

    @Test
    @DisplayName("Verify NLP keyword auto-categorization across distinct spending domains")
    void testAutoDetectCategory() {
        assertEquals(ExpenseCategory.FOOD, expenseAnalyticsService.autoDetectCategory("Swiggy Biryani Order"));
        assertEquals(ExpenseCategory.FOOD, expenseAnalyticsService.autoDetectCategory("Zomato dinner with friends"));
        assertEquals(ExpenseCategory.SHOPPING, expenseAnalyticsService.autoDetectCategory("Amazon Electronics purchase"));
        assertEquals(ExpenseCategory.BILLS, expenseAnalyticsService.autoDetectCategory("Electricity Bill and Water utility payment"));
        assertEquals(ExpenseCategory.TRAVEL, expenseAnalyticsService.autoDetectCategory("Uber ride to Terminal 3 airport"));
        assertEquals(ExpenseCategory.EDUCATION, expenseAnalyticsService.autoDetectCategory("Udemy course and university fees"));
        assertEquals(ExpenseCategory.ENTERTAINMENT, expenseAnalyticsService.autoDetectCategory("Netflix premium subscription"));
        assertEquals(ExpenseCategory.HEALTHCARE, expenseAnalyticsService.autoDetectCategory("Apollo pharmacy medicine and doctor"));
        assertEquals(ExpenseCategory.OTHER, expenseAnalyticsService.autoDetectCategory("General ledger transfer"));
    }

    @Test
    @DisplayName("Verify analytics response structure and category breakdown initialized properly")
    void testGetAnalyticsForUser_EmptyTransactions_ReturnsDefaultBreakdown() {
        when(accountRepository.findByUserUsername("rahul")).thenReturn(Collections.emptyList());

        ExpenseAnalyticsResponse response = expenseAnalyticsService.getAnalyticsForUser("rahul");

        assertNotNull(response);
        assertNotNull(response.getCategoryBreakdown());
        assertEquals(8, response.getCategoryBreakdown().size(), "Should have all 8 expense categories in breakdown");
        assertNotNull(response.getTrendMonths());
        assertEquals(6, response.getTrendMonths().size(), "Should have 6 months of trend data");
        assertNotNull(response.getInsights());
    }
}
