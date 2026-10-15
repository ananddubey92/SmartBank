package com.project.onlinebankingsystem.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String ifscCode;

    private String bankName;

    private java.time.LocalDateTime createdAt;

    @Builder.Default
    private Boolean confirmed = true;

    @Builder.Default
    private Integer transferCount = 0;

    @Builder.Default
    private java.math.BigDecimal totalTransferred = java.math.BigDecimal.ZERO;

    private java.time.LocalDateTime lastTransactionAt;

    @Builder.Default
    private String riskStatus = "NORMAL";

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = java.time.LocalDateTime.now();
        }
        if (confirmed == null) {
            confirmed = true;
        }
        if (transferCount == null) {
            transferCount = 0;
        }
        if (totalTransferred == null) {
            totalTransferred = java.math.BigDecimal.ZERO;
        }
        if (riskStatus == null) {
            riskStatus = "NORMAL";
        }
    }
}
