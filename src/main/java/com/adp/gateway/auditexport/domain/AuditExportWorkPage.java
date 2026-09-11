package com.adp.gateway.auditexport.domain;

import java.util.List;

public record AuditExportWorkPage(
    List<AuditExportJob> items,
    int page,
    int size,
    long totalElements
) {
    public AuditExportWorkPage {
        items = List.copyOf(items);
    }
}
