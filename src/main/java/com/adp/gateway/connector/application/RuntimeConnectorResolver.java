package com.adp.gateway.connector.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.adp.gateway.egress.application.PackRuntimeAdapterNotFoundException;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class RuntimeConnectorResolver {

    private final Map<ExecutionPackType, RuntimeConnectorPort> connectors;

    public RuntimeConnectorResolver(List<RuntimeConnectorPort> connectors) {
        Map<ExecutionPackType, RuntimeConnectorPort> indexed = new EnumMap<>(ExecutionPackType.class);
        connectors.forEach(connector -> {
            RuntimeConnectorPort previous = indexed.putIfAbsent(connector.supportedPack(), connector);
            if (previous != null) {
                throw new IllegalStateException("Duplicate connector for pack " + connector.supportedPack());
            }
        });
        this.connectors = Map.copyOf(indexed);
    }

    public RuntimeConnectorPort resolve(ExecutionPackType packType) {
        RuntimeConnectorPort connector = connectors.get(packType);
        if (connector == null) {
            connector = connectors.get(ExecutionPackType.COMMON);
        }
        if (connector == null) {
            throw new PackRuntimeAdapterNotFoundException("connector", packType);
        }
        return connector;
    }
}
