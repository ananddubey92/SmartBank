package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private String ifscCode;
    private String accountType;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;
    private String userUsername;
    private String userFullName;
    private String accountHolderName;
    private String userEmail;
    private String userPhone;
    private String userPan;
    private String userAadhar;
    private Integer transactionCount;
    private Boolean hasDebitCard;
    private String debitCardNumber;
    private String debitCardStatus;
    private String branchName;
    private String userPinCode;
    private String userKycStatus;
    private String userFatherName;
    private String userMotherName;
    private java.time.LocalDate userDob;
    private String userGender;
    private String userAddress;
    private String nomineeName;
    private String nomineeRelationship;
}
