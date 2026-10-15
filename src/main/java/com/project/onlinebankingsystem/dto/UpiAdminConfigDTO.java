package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiAdminConfigDTO {
    private Boolean upiEnabled;
    private BigDecimal perTransactionLimit;
    private BigDecimal dailyLimit;

    // Super Admin Dashboard Metrics
    private Long totalUpiProfiles;
    private Long activeUpiProfiles;
    private Long totalUpiTransactions;
    private Long successfulUpiTransactions;
    private Long failedUpiTransactions;
    private Long pendingUpiTransactions;
    private Long blockedUpiTransactions;
    private BigDecimal totalUpiVolume;
    private Long suspiciousUpiAttemptsCount;
    private Long lockedUpiProfilesCount;

    private List<UpiTransactionResponse> recentUpiTransactions;
    private List<UpiProfileResponse> lockedUpiProfiles;
}
