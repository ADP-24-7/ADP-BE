package com.adp.gateway.observability.domain;

import java.util.List;

public record PolicyOperationEventPage(
    List<PolicyOperationEvent> items,
    int page,
    int size,
    long total
) {
    public PolicyOperationEventPage {
        items = List.copyOf(items);
    }
}
