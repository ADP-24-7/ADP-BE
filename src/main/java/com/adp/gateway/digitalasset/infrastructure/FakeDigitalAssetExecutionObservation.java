package com.adp.gateway.digitalasset.infrastructure;

import java.time.OffsetDateTime;

import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetFinalityStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReceiptStatus;

record FakeDigitalAssetExecutionObservation(
    String transactionHash,
    DigitalAssetDescriptor asset,
    String recipientAddress,
    DigitalAssetAmount nativeValue,
    DigitalAssetAmount transferredAmount,
    DigitalAssetReceiptStatus receiptStatus,
    DigitalAssetFinalityStatus finalityStatus,
    String transferReference,
    String internalTraceReference,
    OffsetDateTime executedAt,
    OffsetDateTime finalizedAt
) {
}
