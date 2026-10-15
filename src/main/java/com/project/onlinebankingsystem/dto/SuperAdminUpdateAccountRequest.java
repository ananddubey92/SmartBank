package com.project.onlinebankingsystem.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminUpdateAccountRequest {

    private String accountType; // SAVING, CURRENT, FD, RD
    private String status;      // ACTIVE, SUSPENDED, CLOSED
    private String accountHolderName;
    private String ifscCode;
}
