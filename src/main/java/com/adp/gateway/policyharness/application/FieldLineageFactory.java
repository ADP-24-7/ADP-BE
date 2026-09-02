package com.adp.gateway.policyharness.application;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.policyharness.domain.FieldLineage;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.stereotype.Component;

@Component
public class FieldLineageFactory {

    private final CanonicalValueHasher hasher;

    public FieldLineageFactory(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    public FieldLineage create(
        RetrievalResult retrieval,
        CanonicalContext context,
        TransformResult transformResult,
        OutboundCandidatePayload outboundPayload
    ) {
        Set<String> requested = retrieval.selectedFields().stream()
            .map(field -> field.datasetName() + "." + field.fieldName())
            .collect(Collectors.toCollection(TreeSet::new));
        if (context.fields().stream().anyMatch(field -> "request".equals(field.datasetName()))) {
            requested.add("request.prompt");
        }
        Set<String> retrieved = context.fields().stream()
            .map(field -> field.datasetName() + "." + field.fieldName())
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> transformed = transformResult.fields().stream()
            .map(field -> field.datasetName() + "." + field.fieldName())
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> released = outboundPayload == null
            ? new TreeSet<>()
            : outboundPayload.fields().stream()
                .map(field -> semanticPath(field.path()))
                .collect(Collectors.toCollection(TreeSet::new));
        return new FieldLineage(
            requested,
            retrieved,
            transformed,
            released,
            digest(requested),
            digest(retrieved),
            digest(transformed),
            digest(released)
        );
    }

    private String semanticPath(String path) {
        if (path.startsWith("$.input.")) {
            return "request." + path.substring("$.input.".length());
        }
        int recordsMarker = path.indexOf("].");
        return recordsMarker >= 0 ? path.substring(recordsMarker + 2) : path;
    }

    private String digest(Collection<String> fields) {
        return hasher.hash(String.join("|", new TreeSet<>(fields)));
    }
}
