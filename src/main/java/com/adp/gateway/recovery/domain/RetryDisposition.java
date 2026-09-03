package com.adp.gateway.recovery.domain;

public enum RetryDisposition {
    RETRY_ALLOWED,
    RECONCILE_FIRST,
    NO_RETRY,
    MANUAL_REVIEW
}
