package com.project.onlinebankingsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebitCardBackfillResultDTO {
    private int totalAccountsScanned;
    private int eligibleAccountsCount;
    private int generatedCount;
    private int skippedCount;           // Already had card
    private int ineligibleSkippedCount; // Suspended / Closed
    private int failedCount;
    private String status;             // SUCCESS, PARTIAL_SUCCESS, COMPLETED_NO_OP, FAILED
    private String summaryMessage;

    @Builder.Default
    private List<DebitCardResponse> generatedCards = new ArrayList<>();

    @Builder.Default
    private List<BackfillItemDetail> processedDetails = new ArrayList<>();

    @Builder.Default
    private List<String> errorMessages = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackfillItemDetail {
        private String accountNumber;
        private String customerUsername;
        private String customerName;
        private String accountStatus;
        private String result;          // GENERATED, SKIPPED_ALREADY_EXISTS, SKIPPED_INELIGIBLE, FAILED
        private String maskedCardNumber;
        private String message;
    }
}
