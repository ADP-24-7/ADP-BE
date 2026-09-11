package com.adp.gateway.auditexport.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.audit.application.AuditReadService;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auditexport.domain.AuditExportDetail;
import com.adp.gateway.auditexport.domain.AuditExportDownload;
import com.adp.gateway.auditexport.domain.AuditExportFormat;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import com.adp.gateway.auditexport.domain.AuditExportReportType;
import com.adp.gateway.auditexport.domain.AuditExportScope;
import com.adp.gateway.auditexport.domain.AuditExportStatus;
import com.adp.gateway.auditexport.domain.AuditExportWorkPage;
import com.adp.gateway.auditexport.domain.AuditExportWorkSummary;
import com.adp.gateway.auditexport.domain.AuditExportWorkView;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuditExportService {
    private final AuditReadService auditReadService;
    private final AuditExportPersistence persistence;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditExportService(
        AuditReadService auditReadService,
        AuditExportPersistence persistence,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.auditReadService = auditReadService;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AuditExportJob request(
        AuthPrincipal principal,
        String executionId,
        AuditExportReportType reportType,
        AuditExportFormat format,
        String reason,
        String idempotencyKey,
        String requestId,
        String traceId
    ) {
        requireExportRole(principal);
        requireInstitution(principal);
        ExecutionEvidencePack evidence = auditReadService.evidence(principal, executionId);
        AuditExportScope scope = persistence.resolveExecutionScope(
            executionId, principal.institutionId(), principal.workloadIds()
        );
        String normalizedReason = reason.trim();
        String scopeJson = scopeJson(scope, reportType, format, normalizedReason);
        String scopeDigest = sha256(scopeJson);
        return persistence.reserve(new AuditExportReservation(
            "exp_" + UUID.randomUUID(), principal.institutionId(), evidence.workloadId(),
            scope.executionPack(), executionId, reportType.name(), format, scopeDigest, scopeJson,
            principal.principalId(), normalizedReason, idempotencyKey.trim(), requestId, traceId,
            OffsetDateTime.now(clock)
        ));
    }

    public AuditExportDetail get(AuthPrincipal principal, String exportId) {
        requireExportRole(principal);
        requireInstitution(principal);
        return persistence.load(exportId, principal.institutionId(), principal.workloadIds());
    }

    public AuditExportWorkPage searchWork(
        AuthPrincipal principal,
        AuditExportWorkView view,
        AuditExportStatus status,
        int page,
        int size
    ) {
        requireExportRole(principal);
        requireInstitution(principal);
        boolean privileged = principal.hasRole(AdpRole.PRIVILEGED_OPERATOR);
        if ((view == AuditExportWorkView.APPROVAL_QUEUE || view == AuditExportWorkView.HISTORY) && !privileged) {
            throw new AccessDeniedException("Privileged operator role is required for approval work");
        }
        return persistence.searchWork(
            principal.institutionId(), principal.workloadIds(), principal.principalId(), privileged,
            view, status, page, size
        );
    }

    public AuditExportWorkSummary summarizeWork(AuthPrincipal principal) {
        requireExportRole(principal);
        requireInstitution(principal);
        boolean privileged = principal.hasRole(AdpRole.PRIVILEGED_OPERATOR);
        return persistence.summarizeWork(
            principal.institutionId(), principal.workloadIds(), principal.principalId(), privileged,
            OffsetDateTime.now(clock)
        );
    }

    public AuditExportJob approve(
        AuthPrincipal principal,
        String exportId,
        String reason,
        String requestId,
        String traceId
    ) {
        requirePrivileged(principal);
        requireInstitution(principal);
        AuditExportJob job = persistence.load(exportId, principal.institutionId(), principal.workloadIds()).job();
        auditReadService.evidence(principal, job.executionId());
        requireCurrentGrant(principal, job, true);
        if (principal.principalId().equals(job.requesterId())) {
            throw new AuditExportException("AUDIT_EXPORT_MAKER_CHECKER_VIOLATION", "Requester cannot approve export");
        }
        return persistence.approve(exportId, principal.institutionId(), principal.workloadIds(),
            principal.principalId(), reason.trim(), requestId, traceId, OffsetDateTime.now(clock));
    }

    public AuditExportJob reject(
        AuthPrincipal principal,
        String exportId,
        String reason,
        String requestId,
        String traceId
    ) {
        requirePrivileged(principal);
        requireInstitution(principal);
        AuditExportJob job = persistence.load(exportId, principal.institutionId(), principal.workloadIds()).job();
        requireCurrentGrant(principal, job, true);
        return persistence.reject(exportId, principal.institutionId(), principal.workloadIds(),
            principal.principalId(), reason.trim(), requestId, traceId, OffsetDateTime.now(clock));
    }

    public AuditExportJob revoke(
        AuthPrincipal principal,
        String exportId,
        String reason,
        String requestId,
        String traceId
    ) {
        requirePrivileged(principal);
        requireInstitution(principal);
        AuditExportJob job = persistence.load(exportId, principal.institutionId(), principal.workloadIds()).job();
        requireCurrentGrant(principal, job, true);
        return persistence.revoke(exportId, principal.institutionId(), principal.workloadIds(),
            principal.principalId(), reason.trim(), requestId, traceId, OffsetDateTime.now(clock));
    }

    public AuditExportDownload download(
        AuthPrincipal principal,
        String exportId,
        String requestId,
        String traceId
    ) {
        requireExportRole(principal);
        requireInstitution(principal);
        AuditExportJob job = persistence.load(exportId, principal.institutionId(), principal.workloadIds()).job();
        requireCurrentGrant(principal, job, false);
        return persistence.download(exportId, principal.institutionId(), principal.workloadIds(),
            principal.principalId(), requestId, traceId, OffsetDateTime.now(clock));
    }

    private String scopeJson(
        AuditExportScope scope,
        AuditExportReportType reportType,
        AuditExportFormat format,
        String requestReason
    ) {
        try {
            Map<String, String> values = new TreeMap<>();
            values.put("executionId", scope.executionId());
            values.put("executionPack", scope.executionPack().name());
            values.put("format", format.name());
            values.put("institutionId", scope.institutionId());
            values.put("reportType", reportType.name());
            values.put("requestReason", requestReason);
            values.put("workloadId", scope.workloadId());
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize audit export scope", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void requireExportRole(AuthPrincipal principal) {
        if (!principal.hasRole(AdpRole.PRIVILEGED_OPERATOR) && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new AccessDeniedException("Evidence export role is required");
        }
    }

    private void requirePrivileged(AuthPrincipal principal) {
        if (!principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)) {
            throw new AccessDeniedException("Privileged operator role is required");
        }
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Institution scope is required");
        }
    }

    private void requireCurrentGrant(AuthPrincipal principal, AuditExportJob job, boolean privilegedRequired) {
        if (!persistence.principalStillAuthorized(
            principal.principalId(), principal.institutionId(), job.workloadId(), privilegedRequired
        )) {
            throw new AuditExportException("AUDIT_EXPORT_ACCESS_REVOKED", "Audit export grant was revoked");
        }
    }
}
