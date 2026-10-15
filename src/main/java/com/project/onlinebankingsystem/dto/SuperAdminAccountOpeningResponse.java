package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminAccountOpeningResponse {

    // Account Details
    private String accountNumber;
    private String accountType;
    private String branchName;
    private String ifscCode;
    private BigDecimal balance;
    private String accountStatus;
    private LocalDateTime createdAt;

    // Customer Identity & Credentials
    private String customerUsername;
    private String customerFullName;
    private String customerEmail;
    private String customerPhone;
    private String maskedPan;
    private String maskedAadhaar;
    private String kycStatus;
    private String temporaryPassword;
    private Boolean isNewCustomer;

    // Debit Card Details
    private Boolean hasDebitCard;
    private String debitCardNumber;
    private String debitCardType;
    private String debitCardStatus;
    private String debitCardExpiry;

    // Status message
    private String message;
}
