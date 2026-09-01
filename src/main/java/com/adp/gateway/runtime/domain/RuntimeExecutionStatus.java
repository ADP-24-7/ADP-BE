package com.adp.gateway.runtime.domain;

public enum RuntimeExecutionStatus {
    RECEIVED,
    AUTHORIZED,
    RETRIEVED,
    DECIDED,
    TRANSFORMED,
    EGRESSING,
    COMPLETED,
    REVIEW_REQUIRED,
    BLOCKED,
    FAILED
}
