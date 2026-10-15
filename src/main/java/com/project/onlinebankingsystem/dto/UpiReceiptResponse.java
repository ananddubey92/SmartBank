package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiReceiptResponse {
    private String bankName;
    private String transactionStatus;
    private String transactionId;
    private String upiReferenceId;
    private String senderName;
    private String senderUpiId;
    private String senderAccountNumber;
    private String receiverName;
    private String receiverUpiId;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private String remarks;
    private String paymentMethod; // e.g. "UPI Payment"
    private Integer riskScore;
    private String riskLevel;
    private String securityVerificationStatus; // e.g. "PIN Verified", "PIN + OTP Verified"
}
