package com.project.onlinebankingsystem.dto;

import com.project.onlinebankingsystem.enums.Gender;
import com.project.onlinebankingsystem.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDetailResponse {
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
    private Role role;
    private Integer failedLoginAttempts;
    private LocalDateTime lastLoginTime;
    private String accountSecurityStatus;
    private Boolean enabled;
    private List<AccountResponse> accounts;
}
