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
public class FinancialHealthResponse {
    private Integer financialHealthScore; // 0 to 100
    private Integer savingsScore; // 0 to 100
    private Integer spendingScore; // 0 to 100
    private Integer transactionRiskScore; // 0 to 100
    private String healthGrade; // EXCELLENT, GOOD, MODERATE, NEEDS_ATTENTION

    private BigDecimal currentMonthIncome;
    private BigDecimal currentMonthSpending;
    private BigDecimal currentMonthSavings;
    private Double savingsRate; // percentage

    private BigDecimal previousMonthSpending;
    private BigDecimal monthOverMonthSpendingChange;
    private Double monthOverMonthPercent;

    private List<String> trendMonths;
    private List<BigDecimal> monthlyIncomeTrend;
    private List<BigDecimal> monthlyExpenseTrend;
    private List<BigDecimal> monthlySavingsTrend;

    private List<CategoryDistributionItem> categoryDistribution;
    private List<String> intelligentInsights;

    private SpendingPredictionResponse spendingPrediction;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryDistributionItem {
        private String category;
        private String displayName;
        private String color;
        private String icon;
        private BigDecimal amount;
        private Double percentage;
    }
}
