package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private Long id;
    private String transactionReference;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String type;
    private String status;
    private String description;
    private LocalDateTime timestamp;
    private Integer riskScore;
    private String riskLevel;
    private String riskReasons;
    private String category;
    private String categoryDisplayName;
    private String categoryIcon;
    private String categoryColor;
    private Boolean verifiedWithOtp;
    private Double fraudProbability;
    private String decision;
    private Integer ruleBasedScore;
    private Integer mlPredictionScore;
}
