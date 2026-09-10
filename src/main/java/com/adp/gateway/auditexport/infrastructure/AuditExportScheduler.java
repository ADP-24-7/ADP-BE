package com.adp.gateway.auditexport.infrastructure;

import java.util.UUID;

import com.adp.gateway.auditexport.application.AuditExportWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.audit-export.scheduler.enabled", havingValue = "true")
public class AuditExportScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuditExportScheduler.class);

    private final AuditExportWorkerService service;
    private final String workerId = "audit-export:" + UUID.randomUUID();

    public AuditExportScheduler(AuditExportWorkerService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${adp.audit-export.scheduler.fixed-delay:2s}")
    public void processNext() {
        try {
            service.processNext(workerId);
        } catch (RuntimeException exception) {
            log.atWarn()
                .addKeyValue("event", "audit_export_scheduler_failure")
                .addKeyValue("outcome", "FAILED")
                .log("Audit export worker failed");
        }
    }
}
