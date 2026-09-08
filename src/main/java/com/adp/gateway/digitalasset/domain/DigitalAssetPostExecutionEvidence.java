package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record DigitalAssetPostExecutionEvidence(
    String executionId,
    DigitalAssetPostExecutionStatus status,
    DigitalAssetEvidenceSourceType evidenceSourceType,
    DigitalAssetExternalStatus externalStatus,
    DigitalAssetProviderStatus providerStatus,
    DigitalAssetReceiptStatus receiptStatus,
    DigitalAssetFinalityStatus finalityStatus,
    String amountSource,
    String transactionDetailDigest,
    String receiptFinalityDigest,
    String transferEvidenceDigest,
    String internalTraceEvidenceDigest,
    String exactAmountDigest,
    String expectedProjectionDigest,
    String actualProjectionDigest,
    List<DigitalAssetMismatchField> mismatchedFields,
    String providerResponseDigest,
    OffsetDateTime observedAt
) {
    public DigitalAssetPostExecutionEvidence {
        mismatchedFields = List.copyOf(mismatchedFields);
    }

    public boolean permitsCompletion() {
        return status == DigitalAssetPostExecutionStatus.VERIFIED;
    }
}
