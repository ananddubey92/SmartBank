package com.project.onlinebankingsystem.enums;

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    SUCCESS, // Alias for COMPLETED for backward compatibility
    FAILED,
    BLOCKED,
    UNDER_REVIEW,
    PENDING_VERIFICATION,
    CANCELLED
}
