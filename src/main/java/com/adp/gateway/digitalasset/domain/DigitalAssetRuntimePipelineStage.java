package com.adp.gateway.digitalasset.domain;

public enum DigitalAssetRuntimePipelineStage {
    APPROVED_TRANSACTION_LOAD,
    ARTIFACT_BINDING,
    OUTBOUND_GUARD,
    EXTERNAL_HANDOFF,
    POST_EXECUTION_REBINDING
}
