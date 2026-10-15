package com.project.onlinebankingsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "upi_profiles", indexes = {
        @Index(name = "idx_upi_id", columnList = "upiId"),
        @Index(name = "idx_upi_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false, length = 100)
    private String upiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_account_id", nullable = false)
    private Account primaryAccount;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPrimary = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @JsonIgnore
    @Column(length = 255)
    private String upiPin; // BCrypt hashed PIN

    @Builder.Default
    @Column(nullable = false)
    private Integer failedPinAttempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isLocked = false;

    private LocalDateTime lockTime;

    @Column(length = 2000)
    private String qrCodeData;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (isPrimary == null) {
            isPrimary = true;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (failedPinAttempts == null) {
            failedPinAttempts = 0;
        }
        if (isLocked == null) {
            isLocked = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
