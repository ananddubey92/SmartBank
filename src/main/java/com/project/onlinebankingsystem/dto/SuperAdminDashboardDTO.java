package com.project.onlinebankingsystem.dto;

import com.project.onlinebankingsystem.model.AuditLog;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminDashboardDTO {

    private long totalCustomers;
    private long totalAdmins;
    private long activeAdmins;
    private long inactiveAdmins;
    private long totalAccounts;
    private long activeAccounts;
    private long frozenAccounts;
    private long blockedAccounts;

    private long totalDebitCards;
    private long activeDebitCards;
    private long blockedDebitCards;
    private long missingDebitCards;

    private long totalTransactions;
    private long successfulTransactions;
    private long failedTransactions;
    private long pendingTransactions;
    private long suspiciousTransactions;
    private long highRiskTransactions;
    private long blockedTransactions;
    private long pendingReviews;

    private long activeUsers;
    private BigDecimal totalLiquidity;
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;
    private BigDecimal totalBankBalance;

    private List<TransactionResponse> recentTransactions;
    private List<UserSummaryDTO> recentRegistrations;
    private List<AuditLog> recentAuditLogs;
    private long pendingKyc;
    private long pendingApprovals;
    private List<AccountResponse> recentAccountOpenings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserSummaryDTO {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String phone;
        private String accountSecurityStatus;
        private String role;
        private Boolean enabled;
    }
}