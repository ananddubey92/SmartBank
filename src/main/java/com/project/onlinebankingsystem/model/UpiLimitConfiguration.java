package com.project.onlinebankingsystem.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "upi_limit_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiLimitConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal perTransactionLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyLimit;

    @Builder.Default
    @Column(nullable = false)
    private Boolean upiEnabled = true;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
        if (perTransactionLimit == null) {
            perTransactionLimit = new BigDecimal("50000.00");
        }
        if (dailyLimit == null) {
            dailyLimit = new BigDecimal("100000.00");
        }
        if (upiEnabled == null) {
            upiEnabled = true;
        }
    }
}
