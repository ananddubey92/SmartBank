package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudSimulationRequest {
    private BigDecimal amount;
    private Boolean newBeneficiary;
    private Boolean previousTransfers;
    private Integer frequencyIn10Min;
    private String transactionTime; // e.g. "02:30" or "14:15"
    private Double balanceDepletionPercent; // e.g. 82.0
    private Integer accountAgeMonths; // e.g. 6
    private Boolean previousSuspiciousActivity;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
}
