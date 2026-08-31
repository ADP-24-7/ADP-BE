package com.adp.gateway.runtime.domain;

public enum RuntimeExecutionStatus {
    RECEIVED,
    AUTHORIZED,
    RETRIEVED,
    DECIDED,
    TRANSFORMED,
    REVIEW_REQUIRED,
    BLOCKED,
    FAILED
}
