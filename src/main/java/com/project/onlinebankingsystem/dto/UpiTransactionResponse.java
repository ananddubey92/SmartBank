package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiTransactionResponse {
    private Long id;
    private String upiReferenceId;
    private String transactionReference;
    private String senderUpiId;
    private String senderName;
    private String senderAccountNumber;
    private String receiverUpiId;
    private String receiverName;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String transactionType; // UPI_TRANSFER, UPI_REQUEST_PAYMENT, UPI_QR_PAYMENT
    private LocalDateTime timestamp;
    private String remarks;
    private String status; // SUCCESS, PENDING, FAILED, REVERSED, BLOCKED
    private Integer riskScore;
    private String riskLevel;
    private String fraudStatus;
    private String failureReason;
    private String category;
    private String categoryDisplayName;
    private String categoryIcon;
    private String categoryColor;
    private Boolean verifiedWithOtp;
    private String direction; // SENT or RECEIVED relative to current user
}
