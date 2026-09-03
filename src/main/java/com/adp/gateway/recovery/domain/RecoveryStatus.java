package com.adp.gateway.recovery.domain;

public enum RecoveryStatus {
    PENDING,
    CLAIMED,
    RETRY_SCHEDULED,
    RECONCILED,
    MANUAL_REVIEW,
    EXHAUSTED
}
