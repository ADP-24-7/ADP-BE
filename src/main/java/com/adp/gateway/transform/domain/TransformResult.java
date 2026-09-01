package com.adp.gateway.transform.domain;

import java.util.List;

public record TransformResult(
    String transformExecutionId,
    boolean applied,
    String status,
    String outputDigest,
    List<TransformFieldResult> fields
) {

    public TransformResult {
        fields = List.copyOf(fields);
    }

    public static TransformResult skipped(String transformExecutionId) {
        return new TransformResult(transformExecutionId, false, "SKIPPED", null, List.of());
    }
}
