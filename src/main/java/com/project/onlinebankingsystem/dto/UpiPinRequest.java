package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiPinRequest {

    private String oldPin;

    @NotBlank(message = "UPI PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "UPI PIN must be 4 or 6 numeric digits")
    private String pin;

    private String confirmPin;

    private String accountPassword; // For security validation during PIN reset/setup

    private String otp; // For PIN reset or verification
}
