package com.adp.gateway.runtime.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DigitalAssetRuntimeInput", description = "DA-P0-3 caller-controlled Digital Asset input")
public record DigitalAssetRuntimeInputContract(
    @Schema(example = "approved-tx-local-001") String approvedTransactionReference,
    @Schema(example = "customer-100") String customerId,
    @Schema(example = "acct-100-1") String accountId,
    DigitalAssetOutboundRequestContract outboundRequest
) {
}
