package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseAnalyticsResponse {

    private BigDecimal currentMonthSpending;
    private BigDecimal previousMonthSpending;
    private BigDecimal monthOverMonthDiff;
    private Double monthOverMonthPercent;
    private String highestCategoryName;
    private BigDecimal highestCategoryAmount;

    private List<CategoryItem> categoryBreakdown;
    private List<String> insights;

    // Chart trend series
    private List<String> trendMonths;
    private List<BigDecimal> inflowTrends;
    private List<BigDecimal> outflowTrends;

    // Overall analytics numbers
    private long totalTransactions;
    private long successfulTransactions;
    private long failedTransactions;
    private long blockedTransactions;
    private BigDecimal totalTransferred;
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;

    // Fraud statistics
    private long lowRiskCount;
    private long mediumRiskCount;
    private long highRiskCount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryItem {
        private String category;
        private String displayName;
        private String icon;
        private String color;
        private BigDecimal amount;
        private double percentage;
    }
}
