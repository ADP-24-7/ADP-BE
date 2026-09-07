package com.adp.gateway.digitalasset.domain;

public enum DigitalAssetReconciliationResult {
    MATCH,
    WAIT,
    RECOVERED,
    MISMATCH,
    CRITICAL_MISMATCH
}
