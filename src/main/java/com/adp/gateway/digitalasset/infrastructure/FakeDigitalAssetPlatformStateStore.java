package com.adp.gateway.digitalasset.infrastructure;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.adp.gateway.connector.domain.ConnectorStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetPlatformStateStore {

    private final ConcurrentMap<String, ConnectorStatus> states = new ConcurrentHashMap<>();

    public void record(String providerCorrelationKey, ConnectorStatus status) {
        states.put(providerCorrelationKey, status);
    }

    public Optional<ConnectorStatus> find(String providerCorrelationKey) {
        return Optional.ofNullable(states.get(providerCorrelationKey));
    }
}
