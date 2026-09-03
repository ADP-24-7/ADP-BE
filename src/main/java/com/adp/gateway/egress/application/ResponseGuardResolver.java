package com.adp.gateway.egress.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class ResponseGuardResolver {

    private final Map<ExecutionPackType, ResponseGuardPort> guards;

    public ResponseGuardResolver(List<ResponseGuardPort> guards) {
        Map<ExecutionPackType, ResponseGuardPort> indexed = new EnumMap<>(ExecutionPackType.class);
        guards.forEach(guard -> {
            ResponseGuardPort previous = indexed.putIfAbsent(guard.supportedPack(), guard);
            if (previous != null) {
                throw new IllegalStateException("Duplicate response guard for pack " + guard.supportedPack());
            }
        });
        this.guards = Map.copyOf(indexed);
    }

    public ResponseGuardPort resolve(ExecutionPackType packType) {
        ResponseGuardPort guard = guards.get(packType);
        if (guard == null) {
            guard = guards.get(ExecutionPackType.COMMON);
        }
        if (guard == null) {
            throw new PackRuntimeAdapterNotFoundException("response guard", packType);
        }
        return guard;
    }
}
