package com.adp.gateway.recovery.application;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ExternalStatusQueryResolver {

    private final List<ExternalStatusQueryPort> ports;

    public ExternalStatusQueryResolver(List<ExternalStatusQueryPort> ports) {
        this.ports = List.copyOf(ports);
        long fallbackCount = ports.stream().filter(ExternalStatusQueryPort::fallback).count();
        if (fallbackCount > 1) {
            throw new AmbiguousExternalStatusQueryAdapterException(
                "Multiple fallback external status query adapters are registered"
            );
        }
    }

    public ExternalStatusQueryPort resolve(String connectorId) {
        List<ExternalStatusQueryPort> matches = ports.stream()
            .filter(port -> !port.fallback() && port.supports(connectorId))
            .toList();
        if (matches.size() > 1) {
            throw new AmbiguousExternalStatusQueryAdapterException(
                "Multiple external status query adapters support connector " + connectorId
            );
        }
        return matches.stream().findFirst()
            .orElseGet(() -> ports.stream()
                .filter(ExternalStatusQueryPort::fallback)
                .findFirst()
                .orElseThrow(ExternalStatusQueryUnavailableException::new));
    }
}
