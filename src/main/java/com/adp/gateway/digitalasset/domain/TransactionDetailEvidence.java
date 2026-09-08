package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record TransactionDetailEvidence(
    String transactionHash,
    DigitalAssetDescriptor asset,
    String recipientAddress,
    DigitalAssetAmount nativeValue,
    OffsetDateTime executedAt,
    String evidenceDigest
) {
}
