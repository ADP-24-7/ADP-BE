package com.adp.gateway.runtime.api;

import java.util.List;

import com.adp.gateway.transform.domain.TransformResult;

public record PrivacySafeOutputResponse(
    String transformExecutionId,
    String status,
    String outputDigest,
    int fieldCount,
    List<PrivacySafeFieldResponse> fields
) {

    public static PrivacySafeOutputResponse from(TransformResult result) {
        return new PrivacySafeOutputResponse(
            result.transformExecutionId(),
            result.status(),
            result.outputDigest(),
            result.fields().size(),
            result.fields().stream()
                .map(PrivacySafeFieldResponse::from)
                .toList()
        );
    }
}
