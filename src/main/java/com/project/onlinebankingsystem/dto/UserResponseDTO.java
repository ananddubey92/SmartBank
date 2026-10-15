package com.project.onlinebankingsystem.dto;

import com.project.onlinebankingsystem.enums.Gender;
import com.project.onlinebankingsystem.enums.KycStatus;
import com.project.onlinebankingsystem.enums.Role;
import com.project.onlinebankingsystem.model.User;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String fatherName;
    private String motherName;
    private LocalDate dob;
    private String pan;
    private String aadhar;
    private String maskedPan;
    private String maskedAadhar;
    private Gender gender;
    private String address;
    private String pinCode;
    private KycStatus kycStatus;
    private Role role;
    private Integer failedLoginAttempts;
    private LocalDateTime lastLoginTime;
    private String accountSecurityStatus;
    private Boolean enabled;

    public static UserResponseDTO fromEntity(User user) {
        if (user == null) return null;
        return UserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fatherName(user.getFatherName())
                .motherName(user.getMotherName())
                .dob(user.getDob())
                .pan(user.getMaskedPan())
                .aadhar(user.getMaskedAadhar())
                .maskedPan(user.getMaskedPan())
                .maskedAadhar(user.getMaskedAadhar())
                .gender(user.getGender())
                .address(user.getAddress())
                .pinCode(user.getPinCode())
                .kycStatus(user.getKycStatus())
                .role(user.getRole())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lastLoginTime(user.getLastLoginTime())
                .accountSecurityStatus(user.getAccountSecurityStatus())
                .enabled(user.getEnabled())
                .build();
    }
}
