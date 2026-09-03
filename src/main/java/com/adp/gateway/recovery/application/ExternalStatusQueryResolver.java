package com.adp.gateway.recovery.application;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ExternalStatusQueryResolver {

    private final List<ExternalStatusQueryPort> ports;

    public ExternalStatusQueryResolver(List<ExternalStatusQueryPort> ports) {
        this.ports = List.copyOf(ports);
    }

    public ExternalStatusQueryPort resolve(String connectorId) {
        return ports.stream()
            .filter(port -> !port.fallback() && port.supports(connectorId))
            .findFirst()
            .orElseGet(() -> ports.stream()
                .filter(ExternalStatusQueryPort::fallback)
                .findFirst()
                .orElseThrow(ExternalStatusQueryUnavailableException::new));
    }
}
