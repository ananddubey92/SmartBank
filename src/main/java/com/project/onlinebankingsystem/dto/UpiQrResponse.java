package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiQrResponse {
    private String upiId;
    private String payeeName;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private String qrCodeDataUrl; // Base64 Data URL string e.g. data:image/png;base64,...
    private String rawUpiUri; // e.g. upi://pay?pa=rahul@smartbank&pn=Rahul%20Sharma
    private BigDecimal defaultAmount;
    private String defaultRemarks;
}
