package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiLimitsResponse {
    private BigDecimal perTransactionLimit;
    private BigDecimal dailyLimit;
    private BigDecimal usedToday;
    private BigDecimal remainingDailyLimit;
    private Boolean upiEnabled;
}
