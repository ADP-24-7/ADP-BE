package com.adp.gateway.auditexport.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.auditexport.domain.AuditExportDetail;
import com.adp.gateway.auditexport.domain.AuditExportDownload;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import com.adp.gateway.auditexport.domain.AuditExportScope;
import com.adp.gateway.auditexport.domain.AuditExportStatus;
import com.adp.gateway.auditexport.domain.AuditExportWorkPage;
import com.adp.gateway.auditexport.domain.AuditExportWorkSummary;
import com.adp.gateway.auditexport.domain.AuditExportWorkView;

public interface AuditExportPersistence {
    AuditExportScope resolveExecutionScope(
        String executionId,
        String institutionId,
        Set<String> allowedWorkloads
    );

    AuditExportJob reserve(AuditExportReservation reservation);

    AuditExportWorkPage searchWork(
        String institutionId,
        Set<String> allowedWorkloads,
        String principalId,
        boolean privileged,
        AuditExportWorkView view,
        AuditExportStatus status,
        int page,
        int size
    );

    AuditExportWorkSummary summarizeWork(
        String institutionId,
        Set<String> allowedWorkloads,
        String principalId,
        boolean privileged,
        boolean operationsAvailable,
        OffsetDateTime now
    );

    AuditExportDetail load(String exportId, String institutionId, Set<String> allowedWorkloads);

    AuditExportJob approve(
        String exportId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorId,
        String reason,
        String requestId,
        String traceId,
        OffsetDateTime now
    );

    AuditExportJob reject(
        String exportId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorId,
        String reason,
        String requestId,
        String traceId,
        OffsetDateTime now
    );

    AuditExportJob revoke(
        String exportId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorId,
        String reason,
        String requestId,
        String traceId,
        OffsetDateTime now
    );

    int expireDue(OffsetDateTime now);

    Optional<AuditExportJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime leaseUntil);

    void complete(
        String exportId,
        String workerId,
        GeneratedAuditExport generated,
        OffsetDateTime generatedAt,
        OffsetDateTime expiresAt
    );

    void fail(String exportId, String workerId, String failureCode, OffsetDateTime now);

    boolean requesterStillAuthorized(AuditExportJob job);

    boolean principalStillAuthorized(
        String principalId,
        String institutionId,
        String workloadId,
        boolean privilegedRequired
    );

    AuditExportDownload download(
        String exportId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorId,
        String requestId,
        String traceId,
        OffsetDateTime now
    );
}
