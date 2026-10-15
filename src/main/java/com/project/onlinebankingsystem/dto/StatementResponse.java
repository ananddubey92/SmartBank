package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementResponse {

    private Long userId;
    private String customerName;
    private String username;
    private String email;
    private String phone;
    
    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String ifscCode;
    private BigDecimal currentBalance;

    private String preset; // TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_YEAR, CUSTOM
    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal openingBalance;
    private BigDecimal totalCredit;
    private BigDecimal totalDebit;
    private BigDecimal closingBalance;

    private Integer totalTransactions;
    private String message;

    @Builder.Default
    private List<StatementTransactionDTO> transactions = new ArrayList<>();
}
