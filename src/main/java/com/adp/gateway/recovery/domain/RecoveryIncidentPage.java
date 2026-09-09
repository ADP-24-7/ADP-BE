package com.adp.gateway.recovery.domain;

import java.util.List;

public record RecoveryIncidentPage(List<RecoveryIncidentSummary> items, int page, int size, long totalElements) {
    public RecoveryIncidentPage {
        items = List.copyOf(items);
    }
}
