package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiQrPayRequest {

    @NotBlank(message = "Recipient UPI ID / payload is required")
    private String recipientUpiId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transaction amount is ₹1.00")
    private BigDecimal amount;

    private String remarks;
    private String category;
    private String upiPin;
    private String otp;
}
