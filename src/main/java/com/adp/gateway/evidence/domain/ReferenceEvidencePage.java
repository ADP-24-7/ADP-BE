package com.adp.gateway.evidence.domain;

import java.util.List;

public record ReferenceEvidencePage(
    List<ReferenceEvidence> items,
    long total,
    int limit,
    int offset
) {
    public ReferenceEvidencePage {
        items = List.copyOf(items);
    }
}
