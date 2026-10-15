package com.project.onlinebankingsystem.model;

import com.project.onlinebankingsystem.enums.ExpenseCategory;
import com.project.onlinebankingsystem.enums.FraudDecision;
import com.project.onlinebankingsystem.enums.RiskLevel;
import com.project.onlinebankingsystem.enums.TransactionStatus;
import com.project.onlinebankingsystem.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private TransactionStatus status;

    private String description;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 30, columnDefinition = "VARCHAR(30)")
    private RiskLevel riskLevel;

    @Column(name = "risk_reasons", length = 1000)
    private String riskReasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50, columnDefinition = "VARCHAR(50)")
    private ExpenseCategory category;

    @Column(name = "verified_with_otp")
    private Boolean verifiedWithOtp;

    @Column(name = "fraud_probability")
    private Double fraudProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private FraudDecision decision;

    @Column(name = "rule_based_score")
    private Integer ruleBasedScore;

    @Column(name = "ml_prediction")
    private Double mlPrediction;

    @Column(name = "fraud_checked_at")
    private LocalDateTime fraudCheckedAt;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
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
        if (status == null) {
            status = TransactionStatus.COMPLETED;
        }
        if (fraudProbability == null) {
            fraudProbability = (double) (riskScore != null ? riskScore : 10);
        }
        if (decision == null) {
            decision = FraudDecision.ALLOW;
        }
        if (ruleBasedScore == null) {
            ruleBasedScore = riskScore != null ? riskScore : 10;
        }
        if (mlPrediction == null) {
            mlPrediction = fraudProbability;
        }
        if (fraudCheckedAt == null) {
            fraudCheckedAt = timestamp != null ? timestamp : LocalDateTime.now();
        }
    }

    @PostLoad
    protected void onPostLoad() {
        if (riskScore == null) {
            riskScore = 10;
        }
        if (riskLevel == null) {
            riskLevel = RiskLevel.LOW;
        }
        if (decision == null) {
            decision = FraudDecision.ALLOW;
        }
        if (fraudProbability == null) {
            fraudProbability = 10.0;
        }
        if (ruleBasedScore == null) {
            ruleBasedScore = 10;
        }
    }

    public Integer getRiskScore() {
        return riskScore != null ? riskScore : 10;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel != null ? riskLevel : RiskLevel.LOW;
    }

    public FraudDecision getDecision() {
        return decision != null ? decision : FraudDecision.ALLOW;
    }

    public Double getFraudProbability() {
        return fraudProbability != null ? fraudProbability : 10.0;
    }
}
