package com.adp.gateway.audit.domain;

import java.util.List;

public record AuditExecutionPage(List<AuditExecutionSummary> items, int page, int size, long totalElements) {

    public AuditExecutionPage {
        items = List.copyOf(items);
    }
}
