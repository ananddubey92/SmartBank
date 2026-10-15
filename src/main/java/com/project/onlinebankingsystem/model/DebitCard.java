package com.project.onlinebankingsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.onlinebankingsystem.config.CvvCryptoConverter;
import com.project.onlinebankingsystem.enums.CardStatus;
import com.project.onlinebankingsystem.enums.CardType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "debit_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_debit_card_number", columnNames = "card_number"),
        @UniqueConstraint(name = "uk_debit_card_account", columnNames = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, length = 20, unique = true)
    private String cardNumber;

    @Column(name = "masked_card_number", nullable = false, length = 25)
    private String maskedCardNumber;

    @Column(name = "card_holder_name", nullable = false, length = 120)
    private String cardHolderName;

    @Convert(converter = CvvCryptoConverter.class)
    @Column(name = "cvv", nullable = false, length = 255)
    private String cvv;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 40)
    private CardType cardType;

    @Column(name = "network", nullable = false, length = 30)
    private String network;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CardStatus status;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "daily_withdrawal_limit", precision = 15, scale = 2)
    private BigDecimal dailyWithdrawalLimit = new BigDecimal("50000.00");

    @Builder.Default
    @Column(name = "daily_online_limit", precision = 15, scale = 2)
    private BigDecimal dailyOnlineLimit = new BigDecimal("100000.00");

    @Builder.Default
    @Column(name = "international_usage")
    private Boolean internationalUsage = false;

    @Builder.Default
    @Column(name = "online_transactions")
    private Boolean onlineTransactions = true;

    @Builder.Default
    @Column(name = "contactless_enabled")
    private Boolean contactlessEnabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CardStatus.ACTIVE;
        }
        if (issueDate == null) {
            issueDate = LocalDate.now();
        }
        if (expiryDate == null) {
            expiryDate = issueDate.plusYears(5);
        }
        if (dailyWithdrawalLimit == null) {
            dailyWithdrawalLimit = new BigDecimal("50000.00");
        }
        if (dailyOnlineLimit == null) {
            dailyOnlineLimit = new BigDecimal("100000.00");
        }
        if (internationalUsage == null) {
            internationalUsage = false;
        }
        if (onlineTransactions == null) {
            onlineTransactions = true;
        }
        if (contactlessEnabled == null) {
            contactlessEnabled = true;
        }
        if (maskedCardNumber == null && cardNumber != null) {
            maskedCardNumber = maskCardNumber(cardNumber);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (maskedCardNumber == null && cardNumber != null) {
            maskedCardNumber = maskCardNumber(cardNumber);
        }
    }

    public static String maskCardNumber(String rawCard) {
        if (rawCard == null) return "";
        String clean = rawCard.replaceAll("\\s+", "");
        if (clean.length() >= 4) {
            String last4 = clean.substring(clean.length() - 4);
            return "**** **** **** " + last4;
        }
        return "**** **** **** ****";
    }

    public String getLastFourDigits() {
        if (cardNumber == null) return "";
        String clean = cardNumber.replaceAll("\\s+", "");
        return clean.length() >= 4 ? clean.substring(clean.length() - 4) : clean;
    }
}
