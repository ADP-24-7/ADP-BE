package com.adp.gateway.context.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.adp.gateway.egress.application.PackRuntimeAdapterNotFoundException;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPackContextBuilderResolver {

    private final Map<ExecutionPackType, ExecutionPackContextBuilder> builders;

    public ExecutionPackContextBuilderResolver(List<ExecutionPackContextBuilder> builders) {
        this.builders = index(builders);
    }

    public ExecutionPackContextBuilder resolve(ExecutionPackType packType) {
        ExecutionPackContextBuilder builder = builders.get(packType);
        if (builder == null) {
            throw new PackRuntimeAdapterNotFoundException("context builder", packType);
        }
        return builder;
    }

    private Map<ExecutionPackType, ExecutionPackContextBuilder> index(List<ExecutionPackContextBuilder> candidates) {
        Map<ExecutionPackType, ExecutionPackContextBuilder> indexed = new EnumMap<>(ExecutionPackType.class);
        candidates.forEach(candidate -> {
            ExecutionPackContextBuilder previous = indexed.putIfAbsent(candidate.supportedPack(), candidate);
            if (previous != null) {
                throw new IllegalStateException("Duplicate context builder for pack " + candidate.supportedPack());
            }
        });
        return Map.copyOf(indexed);
    }
}
