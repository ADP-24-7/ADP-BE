package com.adp.gateway.egress.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class ExternalSchemaMapperResolver {

    private final Map<ExecutionPackType, ExternalSchemaMapper> mappers;

    public ExternalSchemaMapperResolver(List<ExternalSchemaMapper> mappers) {
        Map<ExecutionPackType, ExternalSchemaMapper> indexed = new EnumMap<>(ExecutionPackType.class);
        mappers.forEach(mapper -> {
            ExternalSchemaMapper previous = indexed.putIfAbsent(mapper.supportedPack(), mapper);
            if (previous != null) {
                throw new IllegalStateException("Duplicate schema mapper for pack " + mapper.supportedPack());
            }
        });
        this.mappers = Map.copyOf(indexed);
    }

    public ExternalSchemaMapper resolve(ExecutionPackType packType) {
        ExternalSchemaMapper mapper = mappers.get(packType);
        if (mapper == null) {
            throw new PackRuntimeAdapterNotFoundException("external schema mapper", packType);
        }
        return mapper;
    }
}
