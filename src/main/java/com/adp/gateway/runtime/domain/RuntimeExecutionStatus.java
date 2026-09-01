package com.adp.gateway.runtime.domain;

public enum RuntimeExecutionStatus {
    RECEIVED,
    AUTHORIZED,
    RETRIEVED,
    DECIDED,
    TRANSFORMED,
    OUTBOUND_READY,
    CONNECTOR_EXECUTED,
    REVIEW_REQUIRED,
    BLOCKED,
    FAILED
}
