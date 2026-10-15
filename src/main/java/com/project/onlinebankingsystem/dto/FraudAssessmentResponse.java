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
public class FraudAssessmentResponse {
    private Integer riskScore; // 0 to 100 final hybrid score
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Double fraudProbability; // 0.0 to 100.0 %
    private Integer ruleBasedScore; // 0 to 100
    private Integer mlPredictionScore; // 0 to 100
    private String decision; // ALLOW, REVIEW, BLOCK
    private List<String> reasons;
    private List<RiskFactorItem> factorBreakdown;
    private boolean requiresOtp;
    private BigDecimal transferAmount;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String simulatedOtp; // Demo OTP provided for easy testing in development/sandbox
    private boolean mlServiceConnected;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskFactorItem {
        private String factorName;
        private Integer scoreImpact;
        private String description;
    }
}
