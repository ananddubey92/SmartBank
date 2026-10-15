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
public class UpiAnalyticsResponse {
    private BigDecimal monthlyUpiSpending;
    private BigDecimal totalSentAmount;
    private BigDecimal totalReceivedAmount;
    private Long totalTransactionsCount;
    private Long successfulTransactionsCount;
    private Long failedTransactionsCount;
    private BigDecimal averageTransactionAmount;
    private BigDecimal highestTransactionAmount;
    private Map<String, BigDecimal> categorySpendingMap;
    private List<CategoryItem> categoryBreakdown;

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
        private Double percentage;
    }
}
