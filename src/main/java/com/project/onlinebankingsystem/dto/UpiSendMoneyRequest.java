package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiSendMoneyRequest {

    private String senderUpiId;

    // Beneficiary lookup option 1: UPI ID
    private String recipientUpiId;

    // Beneficiary lookup option 2: Registered Mobile Number
    private String recipientPhone;

    // Beneficiary lookup option 3: Account Number + IFSC
    private String recipientAccountNumber;
    private String recipientIfscCode;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transaction amount is ₹1.00")
    private BigDecimal amount;

    private String remarks;

    private String category; // Optional spending category

    private String upiPin; // Hashed/Verified before final transfer

    private String otp; // High-risk step-up OTP if challenge triggered
}
