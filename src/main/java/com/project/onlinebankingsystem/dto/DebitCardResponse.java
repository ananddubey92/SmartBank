package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitCardResponse {
    private Long id;
    private String cardNumber;        // Masked for security: **** **** **** 1234
    private String rawCardNumber;     // Full unmasked card number: 6075241234567269
    private String formattedCardNumber; // Full unmasked with spaces: 6075 2412 3456 7269
    private String cvv;               // 3-digit CVV: 842
    private String lastFourDigits;
    private String cardHolderName;
    private String cardType;
    private String network;
    private String expiryDateFormatted; // MM/yy (e.g., "10/31")
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String status;
    private String accountNumber;
    private String accountType;
    private String username;
    private BigDecimal dailyWithdrawalLimit;
    private BigDecimal dailyOnlineLimit;
    private Boolean internationalUsage;
    private Boolean onlineTransactions;
    private Boolean contactlessEnabled;
    private LocalDateTime createdAt;
}
