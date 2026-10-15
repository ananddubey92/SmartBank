package com.project.onlinebankingsystem.dto;

import com.project.onlinebankingsystem.model.Beneficiary;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryResponse {
    private Long id;
    private String name;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private Boolean confirmed;
    private LocalDateTime createdAt;
    private Integer transferCount;
    private BigDecimal totalTransferred;
    private String riskStatus;

    public static BeneficiaryResponse fromEntity(Beneficiary b) {
        if (b == null) return null;
        return BeneficiaryResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .accountNumber(b.getAccountNumber())
                .ifscCode(b.getIfscCode())
                .bankName(b.getBankName())
                .confirmed(b.getConfirmed())
                .createdAt(b.getCreatedAt())
                .transferCount(b.getTransferCount())
                .totalTransferred(b.getTotalTransferred())
                .riskStatus(b.getRiskStatus())
                .build();
    }
}
