package com.adp.gateway.digitalasset.domain;

public record TransferExecutionEvidence(
    boolean required,
    boolean present,
    DigitalAssetAmount amount,
    String evidenceDigest
) {
}
