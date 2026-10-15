package com.project.onlinebankingsystem.dto;

import com.project.onlinebankingsystem.model.LoginActivity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityDashboardResponse {
    private String username;
    private LocalDateTime lastLogin;
    private String lastLoginIp;
    private String accountSecurityStatus; // GOOD, ALERT
    private Integer failedLoginAttempts;
    private boolean hasTransactionPin;
    private List<LoginActivity> recentActivities;
    private List<TransactionResponse> suspiciousTransactions;
    private long totalBlockedAttempts;
    private String passwordSecurityRating; // Strong, Moderate, etc.
}
