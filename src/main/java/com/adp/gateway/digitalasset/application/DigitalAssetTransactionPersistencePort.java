package com.adp.gateway.digitalasset.application;

public interface DigitalAssetTransactionPersistencePort {
    void record(
        String executionId,
        String externalRequestId,
        String externalTransactionId,
        String settlementId,
        String settlementStatus,
        String reconciliationResult,
        String providerResponseDigest
    );
}
