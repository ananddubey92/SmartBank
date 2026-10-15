package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOptionDTO {

    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    
    @Builder.Default
    private List<AccountOptionDTO> accounts = new ArrayList<>();
}
