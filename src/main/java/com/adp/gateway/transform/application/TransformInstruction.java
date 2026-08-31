package com.adp.gateway.transform.application;

import java.time.Duration;
import java.util.Map;

import com.adp.gateway.transform.domain.TransformStrategy;

public record TransformInstruction(
    TransformStrategy strategy,
    String strategyVersion,
    String keyVersion,
    String mappingVersion,
    Duration tokenTtl,
    Map<String, String> parameters
) {

    public TransformInstruction {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
