package com.project.onlinebankingsystem.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiProfileResponse {
    private Long id;
    private String upiId;
    private String username;
    private String fullName;
    private String phone;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private Boolean isPrimary;
    private Boolean isActive;
    private Boolean hasPin;
    private Integer failedPinAttempts;
    private Integer remainingPinAttempts;
    private Boolean isLocked;
    private LocalDateTime lockTime;
    private String qrCodeData;
    private LocalDateTime createdAt;
}
