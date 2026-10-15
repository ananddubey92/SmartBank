package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminCreateAccountRequest {

    @NotBlank(message = "Customer username is required")
    private String customerUsername;

    @NotBlank(message = "Account type is required (SAVING, CURRENT, FD, RD)")
    private String accountType;

    private String accountNumber;

    @DecimalMin(value = "0.0", inclusive = true, message = "Initial balance must not be negative")
    private BigDecimal initialBalance;

    private String ifscCode;
}
