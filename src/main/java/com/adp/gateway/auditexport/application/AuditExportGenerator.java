package com.adp.gateway.auditexport.application;

import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auditexport.domain.AuditExportJob;

public interface AuditExportGenerator {
    GeneratedAuditExport generate(AuditExportJob job, ExecutionEvidencePack evidence);
}
