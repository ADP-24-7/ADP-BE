package com.adp.gateway.runtime.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionEvidence;

public record DigitalAssetPostExecutionEvidenceResponse(
    String status,
    String evidenceSourceType,
    String externalStatus,
    String providerStatus,
    String receiptStatus,
    String finalityStatus,
    String amountSource,
    String transactionDetailDigest,
    String receiptFinalityDigest,
    String transferEvidenceDigest,
    String internalTraceEvidenceDigest,
    String exactAmountDigest,
    String expectedProjectionDigest,
    String actualProjectionDigest,
    List<String> mismatchedFields,
    String providerResponseDigest,
    OffsetDateTime observedAt
) {
    public static DigitalAssetPostExecutionEvidenceResponse from(DigitalAssetPostExecutionEvidence value) {
        if (value == null) {
            return null;
        }
        return new DigitalAssetPostExecutionEvidenceResponse(
            value.status().name(), value.evidenceSourceType().name(), value.externalStatus().name(), value.providerStatus().name(),
            value.receiptStatus().name(), value.finalityStatus().name(), value.amountSource(),
            value.transactionDetailDigest(), value.receiptFinalityDigest(), value.transferEvidenceDigest(),
            value.internalTraceEvidenceDigest(), value.exactAmountDigest(), value.expectedProjectionDigest(),
            value.actualProjectionDigest(), value.mismatchedFields().stream().map(Enum::name).toList(),
            value.providerResponseDigest(), value.observedAt()
        );
    }
}
