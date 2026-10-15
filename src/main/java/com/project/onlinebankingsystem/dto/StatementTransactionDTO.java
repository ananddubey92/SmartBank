package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementTransactionDTO {

    private Long transactionId;
    private String referenceNumber;
    private LocalDateTime timestamp;
    private String transactionType; // Deposit, Withdrawal, Fund Transfer, Received, Payment, Failed/Rejected Transaction
    private String rawType;
    private String description;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal transactionAmount;
    private BigDecimal previousBalance;
    private BigDecimal updatedBalance;
    private String status;
    private Boolean isDebit;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String categoryDisplayName;
    private String categoryIcon;
    private String categoryColor;
}
