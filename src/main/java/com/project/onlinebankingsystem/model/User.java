package com.project.onlinebankingsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.onlinebankingsystem.enums.Gender;
import com.project.onlinebankingsystem.enums.KycStatus;
import com.project.onlinebankingsystem.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String phone;

    private String fatherName;
    private String motherName;
    private LocalDate dob;

    @Column(unique = true)
    private String pan;

    @Column(unique = true)
    private String aadhar;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String address;

    private String pinCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.VERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @JsonIgnore
    private String transactionPin;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private java.time.LocalDateTime lastLoginTime;

    @Builder.Default
    private String accountSecurityStatus = "GOOD";

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    @org.hibernate.annotations.ColumnDefault("true")
    private Boolean enabled = true;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Account> accounts = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Beneficiary> beneficiaries = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
        if (accountSecurityStatus == null) {
            accountSecurityStatus = "GOOD";
        }
        if (enabled == null) {
            enabled = true;
        }
        if (kycStatus == null) {
            kycStatus = KycStatus.VERIFIED;
        }
    }

    public String getMaskedPan() {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 5) + "****" + pan.substring(9);
    }

    public String getMaskedAadhar() {
        if (aadhar == null || aadhar.length() < 12) return aadhar;
        return "XXXX-XXXX-" + aadhar.substring(aadhar.length() - 4);
    }
}
