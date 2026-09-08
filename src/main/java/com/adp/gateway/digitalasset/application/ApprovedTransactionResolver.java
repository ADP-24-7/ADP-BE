package com.adp.gateway.digitalasset.application;

import java.util.List;

import com.adp.gateway.digitalasset.domain.ApprovedTransactionSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ApprovedTransactionResolver {
    private final List<ApprovedTransactionPort> ports;

    public ApprovedTransactionResolver(List<ApprovedTransactionPort> ports) {
        this.ports = List.copyOf(ports);
        if (this.ports.size() > 1) {
            throw new IllegalStateException("Multiple approved transaction authorities are configured");
        }
    }

    public ApprovedTransactionSnapshot resolve(ApprovedTransactionLookup lookup) {
        return ports.stream()
            .map(port -> port.find(lookup))
            .flatMap(java.util.Optional::stream)
            .filter(snapshot -> matchesScope(snapshot, lookup))
            .findFirst()
            .orElseThrow(ApprovedTransactionUnavailableException::new);
    }

    private boolean matchesScope(ApprovedTransactionSnapshot snapshot, ApprovedTransactionLookup lookup) {
        return snapshot.approvedTransactionId().equals(lookup.reference().value())
            && snapshot.institutionId().equals(lookup.institutionId())
            && snapshot.subjectRefDigest().equals(lookup.subjectRefDigest())
            && snapshot.workloadId().equals(lookup.workloadId())
            && snapshot.purpose().equals(lookup.purpose());
    }
}
