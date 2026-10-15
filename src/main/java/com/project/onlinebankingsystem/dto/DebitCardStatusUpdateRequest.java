package com.project.onlinebankingsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitCardStatusUpdateRequest {
    @NotBlank(message = "Status is required")
    private String status; // ACTIVE, BLOCKED, SUSPENDED

    private String reason;
}
