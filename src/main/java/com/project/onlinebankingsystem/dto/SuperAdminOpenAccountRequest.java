package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminOpenAccountRequest {

    // Customer Mode: false = New Customer, true = Existing Customer
    @Builder.Default
    private Boolean isExistingCustomer = false;

    private String existingCustomerUsername;

    // Personal Details (required when creating new customer)
    private String fullName;
    private String fatherName;
    private String motherName;
    private String dob; // Format: yyyy-MM-dd
    private String gender; // MALE, FEMALE, OTHER
    private String phone;
    private String email;
    private String address;
    private String pinCode;

    // Identity & KYC Verification
    private String pan;
    private String aadhar;
    @Builder.Default
    private String kycStatus = "VERIFIED"; // VERIFIED, PENDING, REJECTED
    @Builder.Default
    private Boolean identityVerified = true;

    // Bank Account Details
    @Builder.Default
    private String accountType = "SAVING"; // SAVING, CURRENT, FD, RD

    @Builder.Default
    private String branchName = "Main Branch, Mumbai";

    private String nomineeName;
    private String nomineeRelationship;

    @Builder.Default
    private String ifscCode = "OBIN0000101";

    @DecimalMin(value = "0.0", inclusive = true, message = "Initial deposit amount cannot be negative")
    @Builder.Default
    private BigDecimal initialDeposit = BigDecimal.ZERO;

    @Builder.Default
    private String accountStatus = "ACTIVE"; // ACTIVE, PENDING, FROZEN, CLOSED

    // Optional manual account number override; if empty, server generates unique 10-digit number
    private String accountNumber;
}
