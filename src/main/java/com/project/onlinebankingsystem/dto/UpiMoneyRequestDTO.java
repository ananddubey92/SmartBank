package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiMoneyRequestDTO {

    private Long id;
    private String requestReference;
    private String requesterUsername;
    private String requesterFullName;
    private String requesterUpiId;
    private String targetUsername;
    private String targetFullName;
    private String targetUpiId;
    private BigDecimal amount;
    private String remarks;
    private String status; // PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    // Optional field for accepting request with PIN and OTP
    private String upiPin;
    private String otp;
}
