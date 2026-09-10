package com.adp.gateway.recovery.application;

import java.util.List;

import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.stereotype.Component;

@Component
public class ExternalReconciliationEvidenceResolver {
    private final List<ExternalReconciliationEvidencePort> ports;
    private final ExternalStatusQueryResolver statusQueryResolver;

    public ExternalReconciliationEvidenceResolver(
        List<ExternalReconciliationEvidencePort> ports,
        ExternalStatusQueryResolver statusQueryResolver
    ) {
        this.ports = List.copyOf(ports);
        this.statusQueryResolver = statusQueryResolver;
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
        if (matches.isEmpty()) {
            if (statusQueryResolver.resolve(recovery.connectorId()).requiresReconciliationEvidence()) {
                throw new ExternalStatusQueryPermanentException(
                    "Required reconciliation evidence adapter is unavailable"
                );
            }
            return;
        }
        matches.getFirst().reconcile(recovery, status);
    }
}
