package com.adp.gateway.recovery.application;

import java.util.List;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.stereotype.Component;

@Component
public class ExternalReconciliationEvidenceResolver {
    private final List<ExternalReconciliationEvidencePort> ports;

    public ExternalReconciliationEvidenceResolver(List<ExternalReconciliationEvidencePort> ports) {
        this.ports = List.copyOf(ports);
    }

    public void reconcile(ExternalInteractionRecovery recovery, ExternalStatusQueryResult status) {
        List<ExternalReconciliationEvidencePort> matches = ports.stream()
            .filter(port -> port.supports(recovery.connectorId()))
            .toList();
        if (matches.size() > 1) {
            throw new AmbiguousExternalStatusQueryAdapterException(
                "Multiple reconciliation evidence adapters support connector " + recovery.connectorId()
            );
        }
        matches.stream().findFirst().ifPresent(port -> port.reconcile(recovery, status));
    }
}
