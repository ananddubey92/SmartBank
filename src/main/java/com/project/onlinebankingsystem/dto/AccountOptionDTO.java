package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountOptionDTO {

    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String ifscCode;
    private BigDecimal balance;
    private String status;
}
