package com.adp.gateway.recovery.application;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ExternalRetryResolver {

    private final List<ExternalRetryPort> ports;

    public ExternalRetryResolver(List<ExternalRetryPort> ports) {
        this.ports = List.copyOf(ports);
        if (ports.stream().filter(ExternalRetryPort::fallback).count() > 1) {
            throw new AmbiguousExternalStatusQueryAdapterException(
                "Multiple fallback external retry adapters are registered"
            );
        }
    }

    public ExternalRetryPort resolve(String connectorId) {
        List<ExternalRetryPort> matches = ports.stream()
            .filter(port -> !port.fallback() && port.supports(connectorId))
            .toList();
        if (matches.size() > 1) {
            throw new AmbiguousExternalStatusQueryAdapterException(
                "Multiple external retry adapters support connector " + connectorId
            );
        }
        return matches.stream().findFirst()
            .orElseGet(() -> ports.stream()
                .filter(ExternalRetryPort::fallback)
                .findFirst()
                .orElseThrow(ExternalRetryUnavailableException::new));
    }
}
