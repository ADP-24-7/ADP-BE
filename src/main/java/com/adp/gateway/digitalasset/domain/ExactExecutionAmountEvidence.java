package com.adp.gateway.digitalasset.domain;

public record ExactExecutionAmountEvidence(
    DigitalAssetAmount amount,
    String source,
    String evidenceDigest
) {
}
