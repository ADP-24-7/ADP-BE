package com.adp.gateway.auditexport.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.audit.application.AuditReadPort;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuditExportWorkerService {
    private final AuditExportPersistence persistence;
    private final AuditReadPort auditReadPort;
    private final AuditExportGenerator generator;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retention;

    public AuditExportWorkerService(
        AuditExportPersistence persistence,
        AuditReadPort auditReadPort,
        AuditExportGenerator generator,
        Clock clock,
        @Value("${adp.audit-export.lease-duration:30s}") Duration leaseDuration,
        @Value("${adp.audit-export.retention:1h}") Duration retention
    ) {
        this.persistence = persistence;
        this.auditReadPort = auditReadPort;
        this.generator = generator;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.retention = retention;
    }

    public boolean processNext(String workerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        persistence.expireDue(now);
        return persistence.claimNext(workerId, now, now.plus(leaseDuration))
            .map(job -> {
                processClaimed(job, workerId);
                return true;
            })
            .orElse(false);
    }

    private void processClaimed(AuditExportJob job, String workerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            if (!persistence.requesterStillAuthorized(job)) {
                persistence.fail(job.exportId(), workerId, "AUDIT_EXPORT_ACCESS_REVOKED", now);
                return;
            }
            ExecutionEvidencePack evidence = auditReadPort.loadEvidence(
                job.executionId(), job.institutionId(), Set.of(job.workloadId())
            );
            var scope = persistence.resolveExecutionScope(
                job.executionId(), job.institutionId(), Set.of(job.workloadId())
            );
            if (scope.executionPack() != job.executionPack()) {
                persistence.fail(job.exportId(), workerId, "AUDIT_EXPORT_SCOPE_INVALID", now);
                return;
            }
            GeneratedAuditExport generated = generator.generate(job, evidence);
            persistence.complete(job.exportId(), workerId, generated, now, now.plus(retention));
        } catch (AuditExportException exception) {
            persistence.fail(job.exportId(), workerId, exception.reasonCode(), now);
        } catch (RuntimeException exception) {
            persistence.fail(job.exportId(), workerId, "AUDIT_EXPORT_GENERATION_FAILED", now);
        }
    }
}
