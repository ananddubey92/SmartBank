package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUpiIdRequest {

    @NotBlank(message = "UPI handle/id cannot be blank")
    private String customUpiId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;
}
