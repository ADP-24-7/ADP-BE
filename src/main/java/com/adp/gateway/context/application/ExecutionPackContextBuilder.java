package com.adp.gateway.context.application;

import java.util.Map;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.egress.domain.ExecutionPackType;

public interface ExecutionPackContextBuilder {

    ExecutionPackType supportedPack();

    void validate(Map<String, Object> input);

    CanonicalContext merge(CanonicalContext retrievalContext, Map<String, Object> input);
}
