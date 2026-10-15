package com.project.onlinebankingsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.onlinebankingsystem.enums.AccountStatus;
import com.project.onlinebankingsystem.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String ifscCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccountType accountType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccountStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    @Column(name = "branch_name")
    @Builder.Default
    private String branchName = "Main Branch, Mumbai";

    @Column(name = "nominee_name")
    private String nomineeName;

    @Column(name = "nominee_relationship")
    private String nomineeRelationship;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DebitCard debitCard;

    @PrePersist
    @PreUpdate
    protected void onPersistOrUpdate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        if (accountHolderName == null && user != null) {
            accountHolderName = user.getFullName();
        }
        if (branchName == null || branchName.isBlank()) {
            branchName = "Main Branch, Mumbai";
        }
    }
}
