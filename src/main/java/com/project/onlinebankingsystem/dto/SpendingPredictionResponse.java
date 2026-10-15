package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingPredictionResponse {
    private boolean sufficientData;
    private String dataStatusMessage;

    private BigDecimal currentMonthSpending;
    private BigDecimal dailyAverageSpending;
    private BigDecimal predictedMonthEndSpending;
    private BigDecimal predictedNextMonthSpending;

    private Double confidenceScore; // e.g. 85.0%
    private String primaryDriver; // e.g. "Recurring utility bills and shopping patterns"

    // Trend comparisons for chart
    private List<String> comparisonLabels; // e.g. ["Past Month 2", "Last Month", "Current Actual", "Current Projected", "Next Month Forecast"]
    private List<BigDecimal> actualSpendingPoints;
    private List<BigDecimal> predictedSpendingPoints;

    public String getPredictionSummary() {
        return dataStatusMessage != null ? dataStatusMessage : "Predicted spending calculated from historical velocity.";
    }
}
