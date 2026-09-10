package com.adp.gateway.auditexport.domain;

import java.util.List;

public record AuditExportDetail(AuditExportJob job, List<AuditExportEvent> events) {
    public AuditExportDetail {
        events = List.copyOf(events);
    }
}
