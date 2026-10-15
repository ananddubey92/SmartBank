package com.project.onlinebankingsystem.model;

import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionType;
import com.project.onlinebankingsystem.enums.UpiTransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "upi_transactions", indexes = {
        @Index(name = "idx_upi_tx_ref", columnList = "upiReferenceId"),
        @Index(name = "idx_upi_sender", columnList = "senderUpiId"),
        @Index(name = "idx_upi_receiver", columnList = "receiverUpiId"),
        @Index(name = "idx_upi_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String upiReferenceId;

    @Column(nullable = false, length = 64)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User senderUser;

    @Column(nullable = false, length = 100)
    private String senderUpiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id")
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_user_id")
    private User receiverUser;

    @Column(nullable = false, length = 100)
    private String receiverUpiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id")
    private Account receiverAccount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private TransactionType transactionType; // UPI_TRANSFER, UPI_REQUEST_PAYMENT, UPI_QR_PAYMENT

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 255)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private UpiTransactionStatus status;

    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RiskLevel riskLevel;

    @Column(length = 50)
    private String fraudStatus;

    @Column(length = 500)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ExpenseCategory category;

    @Builder.Default
    private Boolean verifiedWithOtp = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = UpiTransactionStatus.SUCCESS;
        }
        if (riskScore == null) {
            riskScore = 10;
        }
        if (riskLevel == null) {
            riskLevel = RiskLevel.LOW;
        }
        if (category == null) {
            category = ExpenseCategory.OTHER;
        }
        if (verifiedWithOtp == null) {
            verifiedWithOtp = false;
        }
    }
}
