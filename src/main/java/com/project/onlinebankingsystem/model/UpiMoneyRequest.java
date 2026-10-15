package com.project.onlinebankingsystem.model;

import com.project.onlinebankingsystem.enums.UpiRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "upi_money_requests", indexes = {
        @Index(name = "idx_upi_req_ref", columnList = "requestReference"),
        @Index(name = "idx_upi_req_target", columnList = "target_user_id"),
        @Index(name = "idx_upi_req_requester", columnList = "requester_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiMoneyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String requestReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requesterUser;

    @Column(nullable = false, length = 100)
    private String requesterUpiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(nullable = false, length = 100)
    private String targetUpiId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UpiRequestStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = UpiRequestStatus.PENDING;
        }
    }
}
