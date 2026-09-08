package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record ReceiptFinalityEvidence(
    DigitalAssetReceiptStatus receiptStatus,
    DigitalAssetFinalityStatus finalityStatus,
    OffsetDateTime finalizedAt,
    String evidenceDigest
) {
    public boolean isFinalSuccess() {
        return receiptStatus == DigitalAssetReceiptStatus.SUCCESS
            && finalityStatus == DigitalAssetFinalityStatus.FINALIZED
            && finalizedAt != null;
    }
}
