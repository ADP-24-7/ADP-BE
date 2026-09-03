package com.adp.gateway.runtime.domain;

public enum RuntimeExecutionStatus {
    RECEIVED,
    AUTHORIZED,
    RETRIEVED,
    DECIDED,
    TRANSFORMED,
    EGRESSING,
    EXTERNALLY_RECONCILED,
    COMPLETED,
    REVIEW_REQUIRED,
    BLOCKED,
    FAILED
}
