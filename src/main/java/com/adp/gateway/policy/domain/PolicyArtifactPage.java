package com.adp.gateway.policy.domain;

import java.util.List;

public record PolicyArtifactPage(
    List<PolicyArtifactSummary> items,
    long total,
    int limit,
    int offset
) {
    public PolicyArtifactPage {
        items = List.copyOf(items);
    }
}
