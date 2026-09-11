package com.adp.gateway.auditexport.infrastructure;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.auditexport.application.AuditExportException;
import com.adp.gateway.auditexport.application.AuditExportPersistence;
import com.adp.gateway.auditexport.application.AuditExportReservation;
import com.adp.gateway.auditexport.application.GeneratedAuditExport;
import com.adp.gateway.auditexport.domain.AuditExportDetail;
import com.adp.gateway.auditexport.domain.AuditExportDownload;
import com.adp.gateway.auditexport.domain.AuditExportEvent;
import com.adp.gateway.auditexport.domain.AuditExportFormat;
import com.adp.gateway.auditexport.domain.AuditExportJob;
import com.adp.gateway.auditexport.domain.AuditExportStatus;
import com.adp.gateway.auditexport.domain.AuditExportScope;
import com.adp.gateway.auditexport.domain.AuditExportWorkPage;
import com.adp.gateway.auditexport.domain.AuditExportWorkSummary;
import com.adp.gateway.auditexport.domain.AuditExportWorkView;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAuditExportPersistence implements AuditExportPersistence {
    private static final RowMapper<AuditExportJob> JOB_MAPPER = JdbcAuditExportPersistence::mapJob;
    private static final RowMapper<AuditExportEvent> EVENT_MAPPER = (rs, rowNum) -> new AuditExportEvent(
        rs.getString("event_id"), rs.getString("actor_id"), rs.getString("request_id"),
        rs.getString("trace_id"), rs.getString("action"), nullableStatus(rs.getString("from_status")),
        AuditExportStatus.valueOf(rs.getString("to_status")), rs.getString("reason_code"),
        rs.getString("reason_text"),
        rs.getObject("occurred_at", OffsetDateTime.class)
    );

    private final JdbcClient jdbcClient;

    public JdbcAuditExportPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AuditExportScope resolveExecutionScope(
        String executionId, String institutionId, Set<String> allowedWorkloads
    ) {
        String workloadScope = allowedWorkloads.contains("*") ? ""
            : allowedWorkloads.isEmpty() ? " and 1 = 0" : " and workload_id in (:allowedWorkloads)";
        JdbcClient.StatementSpec spec = jdbcClient.sql("""
                select institution_id, workload_id, execution_pack, execution_id
                from runtime.runtime_execution
                where execution_id = :executionId and institution_id = :institutionId
                  and execution_pack is not null
                """ + workloadScope)
            .param("executionId", executionId).param("institutionId", institutionId);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            spec = spec.param("allowedWorkloads", allowedWorkloads);
        }
        return spec.query((rs, rowNum) -> new AuditExportScope(
                rs.getString("institution_id"), rs.getString("workload_id"),
                ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("execution_id")
            ))
            .optional()
            .orElseThrow(() -> new AuditExportException("AUDIT_EXPORT_SCOPE_INVALID",
                "Execution scope is unavailable"));
    }

    @Override
    @Transactional
    public AuditExportJob reserve(AuditExportReservation reservation) {
        int inserted = jdbcClient.sql("""
                insert into audit_export_job (
                    export_id, institution_id, workload_id, execution_pack, execution_id,
                    report_type, export_format, status, scope_digest, requester_id,
                    request_reason, idempotency_key, scope_json, created_at, updated_at
                ) values (
                    :exportId, :institutionId, :workloadId, :executionPack, :executionId,
                    :reportType, :format, 'REQUESTED', :scopeDigest, :requesterId,
                    :requestReason, :idempotencyKey, :scopeJson, :now, :now
                ) on conflict (institution_id, requester_id, idempotency_key) do nothing
                """)
            .param("exportId", reservation.exportId())
            .param("institutionId", reservation.institutionId())
            .param("workloadId", reservation.workloadId())
            .param("executionPack", reservation.executionPack().name())
            .param("executionId", reservation.executionId())
            .param("reportType", reservation.reportType())
            .param("format", reservation.format().name())
            .param("scopeDigest", reservation.scopeDigest())
            .param("requesterId", reservation.requesterId())
            .param("requestReason", reservation.requestReason())
            .param("idempotencyKey", reservation.idempotencyKey())
            .param("scopeJson", reservation.scopeJson())
            .param("now", reservation.now())
            .update();

        AuditExportJob job = inserted == 1
            ? loadById(reservation.exportId())
            : jdbcClient.sql("""
                    select * from audit_export_job
                    where institution_id = :institutionId
                      and requester_id = :requesterId
                      and idempotency_key = :idempotencyKey
                    """)
                .param("institutionId", reservation.institutionId())
                .param("requesterId", reservation.requesterId())
                .param("idempotencyKey", reservation.idempotencyKey())
                .query(JOB_MAPPER).single();
        if (!job.scopeDigest().equals(reservation.scopeDigest())) {
            throw new AuditExportException("AUDIT_EXPORT_IDEMPOTENCY_CONFLICT",
                "Idempotency key was reused with a different export scope");
        }
        if (inserted == 1) {
            event(job.exportId(), job.institutionId(), reservation.requesterId(), reservation.requestId(),
                reservation.traceId(), "REQUESTED", null, AuditExportStatus.REQUESTED,
                "AUDIT_EXPORT_REQUESTED", reservation.requestReason(), reservation.now());
        }
        return job;
    }

    @Override
    public AuditExportWorkPage searchWork(
        String institutionId,
        Set<String> allowedWorkloads,
        String principalId,
        boolean privileged,
        AuditExportWorkView view,
        AuditExportStatus status,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where institution_id = :institutionId");
        appendWorkloadScope(where, allowedWorkloads);
        switch (view) {
            case MY_REQUESTS -> where.append("""
                 and requester_id = :principalId
                 and (
                   status in ('REQUESTED', 'APPROVED', 'GENERATING')
                   or (status = 'READY' and downloaded_at is null)
                 )
                """);
            case MY_HISTORY -> where.append("""
                 and requester_id = :principalId
                 and (
                   status in ('REJECTED', 'FAILED', 'EXPIRED', 'REVOKED')
                   or (status = 'READY' and downloaded_at is not null)
                 )
                """);
            case APPROVAL_QUEUE -> where.append(" and status = 'REQUESTED' and requester_id <> :principalId");
            case DECISION_HISTORY -> where.append("""
                 and exists (
                   select 1 from audit_export_event handled
                   where handled.export_id = audit_export_job.export_id
                     and handled.institution_id = audit_export_job.institution_id
                     and handled.actor_id = :principalId
                     and handled.action in ('APPROVED', 'REJECTED', 'REVOKED')
                 )
                """);
            case AUDIT_HISTORY -> { }
        }
        if (status != null) where.append(" and status = :status");

        String orderBy = switch (view) {
            case APPROVAL_QUEUE -> " order by created_at, export_id";
            case MY_REQUESTS -> """
                 order by
                   case
                     when status = 'READY' and downloaded_at is null then 0
                     when status = 'REQUESTED' then 1
                     when status in ('APPROVED', 'GENERATING') then 2
                     else 4
                   end,
                   case when status = 'REQUESTED' then created_at end asc nulls last,
                   case when status <> 'REQUESTED' then updated_at end desc nulls last,
                   export_id desc
                """;
            case MY_HISTORY, DECISION_HISTORY, AUDIT_HISTORY -> " order by updated_at desc, export_id desc";
        };

        JdbcClient.StatementSpec select = bindWorkQuery(
            jdbcClient.sql("select * from audit_export_job" + where
                + orderBy + " limit :size offset :offset"),
            institutionId, allowedWorkloads, principalId, status
        ).param("size", size).param("offset", page * size);
        List<AuditExportJob> items = select.query(JOB_MAPPER).list();
        long total = bindWorkQuery(
            jdbcClient.sql("select count(*) from audit_export_job" + where),
            institutionId, allowedWorkloads, principalId, status
        ).query(Long.class).single();
        return new AuditExportWorkPage(items, page, size, total);
    }

    @Override
    public AuditExportWorkSummary summarizeWork(
        String institutionId,
        Set<String> allowedWorkloads,
        String principalId,
        boolean privileged,
        boolean operationsAvailable,
        OffsetDateTime now
    ) {
        StringBuilder scope = new StringBuilder(" where institution_id = :institutionId");
        appendWorkloadScope(scope, allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select
              count(*) filter (where requester_id = :principalId and status = 'REQUESTED') as my_pending,
              count(*) filter (where requester_id = :principalId and status in ('APPROVED', 'GENERATING')) as my_approved,
              count(*) filter (where requester_id = :principalId and status = 'READY' and downloaded_at is null) as my_ready,
              count(*) filter (where requester_id = :principalId and downloaded_at is not null) as my_downloaded,
              count(*) filter (where requester_id = :principalId and status = 'REJECTED') as my_rejected,
              count(*) filter (where requester_id = :principalId and status in ('FAILED', 'EXPIRED', 'REVOKED')) as my_failed,
              count(*) filter (where status = 'REQUESTED' and requester_id <> :principalId) as approval_pending,
              count(*) filter (where status = 'REQUESTED' and requester_id <> :principalId
                and created_at <= :agingThreshold) as approval_aged,
              count(*) filter (where status = 'REQUESTED') as ops_pending,
              min(created_at) filter (where status = 'REQUESTED') as oldest_pending_at,
              count(*) filter (where approved_at >= :windowStart) as approved_24h,
              count(*) filter (where status = 'REJECTED' and updated_at >= :windowStart) as rejected_24h,
              count(*) filter (where status = 'GENERATING') as generating,
              count(*) filter (where status = 'READY' and downloaded_at is null) as ready,
              count(*) filter (where status = 'FAILED') as failed,
              count(*) filter (where status = 'EXPIRED') as expired
            from audit_export_job
            """ + scope)
            .param("institutionId", institutionId)
            .param("principalId", principalId)
            .param("agingThreshold", now.minusHours(24))
            .param("windowStart", now.minusHours(24));
        statement = bindWorkloads(statement, allowedWorkloads);
        return statement.query((rs, rowNum) -> {
            OffsetDateTime oldest = rs.getObject("oldest_pending_at", OffsetDateTime.class);
            Long oldestAge = oldest == null ? null : Math.max(0, java.time.Duration.between(oldest, now).toSeconds());
            return new AuditExportWorkSummary(
                principalId,
                privileged,
                operationsAvailable,
                new AuditExportWorkSummary.PersonalWork(
                    rs.getLong("my_pending"), rs.getLong("my_approved"), rs.getLong("my_ready"),
                    rs.getLong("my_downloaded"), rs.getLong("my_rejected"), rs.getLong("my_failed")
                ),
                new AuditExportWorkSummary.ApprovalWork(
                    privileged ? rs.getLong("approval_pending") : 0,
                    privileged ? rs.getLong("approval_aged") : 0
                ),
                new AuditExportWorkSummary.Operations(
                    operationsAvailable ? rs.getLong("ops_pending") : 0,
                    operationsAvailable ? oldestAge : null,
                    operationsAvailable ? rs.getLong("approved_24h") : 0,
                    operationsAvailable ? rs.getLong("rejected_24h") : 0,
                    operationsAvailable ? rs.getLong("generating") : 0,
                    operationsAvailable ? rs.getLong("ready") : 0,
                    operationsAvailable ? rs.getLong("failed") : 0,
                    operationsAvailable ? rs.getLong("expired") : 0
                ),
                now
            );
        }).single();
    }

    @Override
    @Transactional
    public AuditExportDetail load(String exportId, String institutionId, Set<String> allowedWorkloads) {
        expire(exportId, institutionId, OffsetDateTime.now());
        AuditExportJob job = loadScoped(exportId, institutionId, allowedWorkloads);
        List<AuditExportEvent> events = jdbcClient.sql("""
                select * from audit_export_event
                where export_id = :exportId and institution_id = :institutionId
                order by occurred_at, event_id
                """)
            .param("exportId", exportId)
            .param("institutionId", institutionId)
            .query(EVENT_MAPPER).list();
        return new AuditExportDetail(job, events);
    }

    @Override
    @Transactional
    public AuditExportJob approve(
        String exportId, String institutionId, Set<String> allowedWorkloads, String actorId,
        String reason, String requestId, String traceId, OffsetDateTime now
    ) {
        AuditExportJob before = loadScoped(exportId, institutionId, allowedWorkloads);
        if (before.status() != AuditExportStatus.REQUESTED) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Export is not awaiting approval");
        }
        if (before.requesterId().equals(actorId)) {
            throw new AuditExportException("AUDIT_EXPORT_MAKER_CHECKER_VIOLATION",
                "Requester cannot approve export");
        }
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = 'APPROVED', approver_id = :actorId, approval_reason = :reason,
                    approved_at = :now, updated_at = :now, version = version + 1
                where export_id = :exportId and institution_id = :institutionId
                  and status = 'REQUESTED' and version = :version
                """)
            .param("actorId", actorId).param("reason", reason).param("now", now)
            .param("exportId", exportId).param("institutionId", institutionId)
            .param("version", before.version()).update();
        if (changed != 1) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Concurrent export transition");
        }
        event(exportId, institutionId, actorId, requestId, traceId, "APPROVED", before.status(),
            AuditExportStatus.APPROVED, "AUDIT_EXPORT_APPROVED", reason, now);
        return loadById(exportId);
    }

    @Override
    @Transactional
    public AuditExportJob reject(
        String exportId, String institutionId, Set<String> allowedWorkloads, String actorId,
        String reason, String requestId, String traceId, OffsetDateTime now
    ) {
        AuditExportJob before = loadScoped(exportId, institutionId, allowedWorkloads);
        if (before.status() != AuditExportStatus.REQUESTED) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Export is not awaiting approval");
        }
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = 'REJECTED', approver_id = :actorId, approval_reason = :reason,
                    updated_at = :now, version = version + 1
                where export_id = :exportId and institution_id = :institutionId
                  and status = 'REQUESTED' and version = :version
                """)
            .param("actorId", actorId).param("reason", reason).param("now", now).param("exportId", exportId)
            .param("institutionId", institutionId).param("version", before.version()).update();
        if (changed != 1) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Concurrent export transition");
        }
        event(exportId, institutionId, actorId, requestId, traceId, "REJECTED", before.status(),
            AuditExportStatus.REJECTED, "AUDIT_EXPORT_REJECTED", reason, now);
        return loadById(exportId);
    }

    @Override
    @Transactional
    public AuditExportJob revoke(
        String exportId, String institutionId, Set<String> allowedWorkloads, String actorId,
        String reason, String requestId, String traceId, OffsetDateTime now
    ) {
        AuditExportJob before = loadScoped(exportId, institutionId, allowedWorkloads);
        if (before.status() != AuditExportStatus.APPROVED
            && before.status() != AuditExportStatus.GENERATING
            && before.status() != AuditExportStatus.READY) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT",
                "Only approved, generating, or ready export can be revoked");
        }
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = 'REVOKED', content = null,
                    revoked_by = :actorId, revoked_at = :now, revocation_reason = :reason,
                    lease_owner = null, lease_until = null,
                    updated_at = :now, version = version + 1
                where export_id = :exportId and institution_id = :institutionId
                  and status in ('APPROVED', 'GENERATING', 'READY') and version = :version
                """)
            .param("actorId", actorId).param("reason", reason).param("now", now).param("exportId", exportId)
            .param("institutionId", institutionId).param("version", before.version()).update();
        if (changed != 1) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Concurrent export transition");
        }
        event(exportId, institutionId, actorId, requestId, traceId, "REVOKED", before.status(),
            AuditExportStatus.REVOKED, "AUDIT_EXPORT_REVOKED", reason, now);
        if (before.status() == AuditExportStatus.READY) {
            event(exportId, institutionId, actorId, requestId, traceId, "DELETED",
                AuditExportStatus.REVOKED, AuditExportStatus.REVOKED, "AUDIT_EXPORT_CONTENT_DELETED", null, now);
        }
        return loadById(exportId);
    }

    @Override
    @Transactional
    public int expireDue(OffsetDateTime now) {
        List<ExpiredExport> expired = jdbcClient.sql("""
                update audit_export_job
                set status = 'EXPIRED', content = null, updated_at = :now, version = version + 1
                where status = 'READY' and expires_at <= :now
                returning export_id, institution_id
                """)
            .param("now", now)
            .query((rs, rowNum) -> new ExpiredExport(rs.getString("export_id"), rs.getString("institution_id")))
            .list();
        expired.forEach(item -> {
            event(item.exportId(), item.institutionId(), "system:expiry", null, null, "EXPIRED",
                AuditExportStatus.READY, AuditExportStatus.EXPIRED, "AUDIT_EXPORT_EXPIRED", null, now);
            event(item.exportId(), item.institutionId(), "system:expiry", null, null, "DELETED",
                AuditExportStatus.EXPIRED, AuditExportStatus.EXPIRED, "AUDIT_EXPORT_CONTENT_DELETED", null, now);
        });
        return expired.size();
    }

    @Override
    @Transactional
    public Optional<AuditExportJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
        jdbcClient.sql("""
                update audit_export_job
                set status = 'APPROVED', lease_owner = null, lease_until = null,
                    updated_at = :now, version = version + 1
                where status = 'GENERATING' and lease_until <= :now
                """).param("now", now).update();
        return jdbcClient.sql("""
                with due as (
                    select export_id from audit_export_job
                    where status = 'APPROVED'
                    order by approved_at, created_at
                    for update skip locked limit 1
                )
                update audit_export_job job
                set status = 'GENERATING', lease_owner = :workerId, lease_until = :leaseUntil,
                    updated_at = :now, version = version + 1
                from due where job.export_id = due.export_id
                returning job.*
                """)
            .param("workerId", workerId).param("leaseUntil", leaseUntil).param("now", now)
            .query(JOB_MAPPER).optional();
    }

    @Override
    @Transactional
    public void complete(
        String exportId, String workerId, GeneratedAuditExport generated,
        OffsetDateTime generatedAt, OffsetDateTime expiresAt
    ) {
        AuditExportJob before = loadById(exportId);
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = 'READY', content = :content, content_digest = :contentDigest,
                    content_size = :contentSize, content_type = :contentType, file_name = :fileName,
                    row_count = :rowCount, generated_at = :generatedAt, expires_at = :expiresAt,
                    lease_owner = null, lease_until = null, updated_at = :generatedAt, version = version + 1
                where export_id = :exportId and status = 'GENERATING' and lease_owner = :workerId
                """)
            .param("content", generated.content()).param("contentDigest", generated.contentDigest())
            .param("contentSize", generated.content().length).param("contentType", generated.contentType())
            .param("fileName", generated.fileName()).param("rowCount", generated.rowCount())
            .param("generatedAt", generatedAt).param("expiresAt", expiresAt)
            .param("exportId", exportId).param("workerId", workerId).update();
        if (changed != 1) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Export lease is stale");
        }
        event(exportId, before.institutionId(), workerId, null, null, "GENERATED", before.status(),
            AuditExportStatus.READY, "AUDIT_EXPORT_GENERATED", null, generatedAt);
    }

    @Override
    @Transactional
    public void fail(String exportId, String workerId, String failureCode, OffsetDateTime now) {
        AuditExportJob before = loadById(exportId);
        AuditExportStatus target = "AUDIT_EXPORT_ACCESS_REVOKED".equals(failureCode)
            ? AuditExportStatus.REVOKED : AuditExportStatus.FAILED;
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = :status, content = null, failure_code = :failureCode,
                    revoked_by = case when :status = 'REVOKED' then :workerId else revoked_by end,
                    revoked_at = case when :status = 'REVOKED' then :now else revoked_at end,
                    revocation_reason = case when :status = 'REVOKED' then :failureCode else revocation_reason end,
                    lease_owner = null, lease_until = null, updated_at = :now, version = version + 1
                where export_id = :exportId and status = 'GENERATING' and lease_owner = :workerId
                """)
            .param("status", target.name()).param("failureCode", failureCode).param("now", now)
            .param("exportId", exportId).param("workerId", workerId).update();
        if (changed == 1) {
            event(exportId, before.institutionId(), workerId, null, null, target.name(), before.status(),
                target, failureCode, failureCode, now);
        }
    }

    @Override
    public boolean requesterStillAuthorized(AuditExportJob job) {
        return principalStillAuthorized(job.requesterId(), job.institutionId(), job.workloadId(), false);
    }

    @Override
    public boolean principalStillAuthorized(
        String principalId, String institutionId, String workloadId, boolean privilegedRequired
    ) {
        return jdbcClient.sql("""
                select exists (
                    select 1 from auth_principal principal
                    where principal.principal_id = :principalId
                      and principal.institution_id = :institutionId
                      and principal.enabled = true
                      and exists (
                          select 1 from auth_principal_role role
                          where role.principal_id = principal.principal_id
                            and role.role_name in (:allowedRoles)
                      )
                      and exists (
                          select 1 from auth_principal_workload workload
                          where workload.principal_id = principal.principal_id
                            and workload.workload_id in (:workloadId, '*')
                      )
                )
                """)
            .param("principalId", principalId).param("institutionId", institutionId)
            .param("workloadId", workloadId)
            .param("allowedRoles", privilegedRequired ? Set.of("PRIVILEGED_OPERATOR")
                : Set.of("OPERATOR", "PRIVILEGED_OPERATOR", "AUDITOR"))
            .query(Boolean.class).single();
    }

    @Override
    @Transactional
    public AuditExportDownload download(
        String exportId, String institutionId, Set<String> allowedWorkloads, String actorId,
        String requestId, String traceId, OffsetDateTime now
    ) {
        AuditExportJob job = loadScopedForUpdate(exportId, institutionId, allowedWorkloads);
        if (job.status() == AuditExportStatus.READY && !job.expiresAt().isAfter(now)) {
            expire(exportId, institutionId, now);
            throw new AuditExportException("AUDIT_EXPORT_EXPIRED", "Export has expired");
        }
        if (job.status() == AuditExportStatus.EXPIRED) {
            throw new AuditExportException("AUDIT_EXPORT_EXPIRED", "Export has expired");
        }
        if (job.status() != AuditExportStatus.READY) {
            throw new AuditExportException("AUDIT_EXPORT_NOT_READY", "Export is not ready");
        }
        byte[] content = jdbcClient.sql("select content from audit_export_job where export_id = :exportId")
            .param("exportId", exportId).query(byte[].class).single();
        if (!sha256(content).equals(job.contentDigest())) {
            throw new AuditExportException("AUDIT_EXPORT_DIGEST_MISMATCH", "Export digest mismatch");
        }
        int changed = jdbcClient.sql("""
                update audit_export_job set downloaded_at = :now, updated_at = :now, version = version + 1
                where export_id = :exportId and institution_id = :institutionId
                  and status = 'READY' and version = :version
                """).param("now", now).param("exportId", exportId)
            .param("institutionId", institutionId).param("version", job.version()).update();
        if (changed != 1) {
            throw new AuditExportException("AUDIT_EXPORT_STATUS_CONFLICT", "Concurrent export transition");
        }
        event(exportId, institutionId, actorId, requestId, traceId, "DOWNLOADED", job.status(),
            job.status(), "AUDIT_EXPORT_DOWNLOADED", null, now);
        return new AuditExportDownload(content, job.contentType(), job.fileName(), job.contentDigest());
    }

    private AuditExportJob loadScoped(String exportId, String institutionId, Set<String> allowedWorkloads) {
        return loadScoped(exportId, institutionId, allowedWorkloads, false);
    }

    private AuditExportJob loadScopedForUpdate(
        String exportId, String institutionId, Set<String> allowedWorkloads
    ) {
        return loadScoped(exportId, institutionId, allowedWorkloads, true);
    }

    private AuditExportJob loadScoped(
        String exportId, String institutionId, Set<String> allowedWorkloads, boolean forUpdate
    ) {
        String workloadScope = allowedWorkloads.contains("*") ? ""
            : allowedWorkloads.isEmpty() ? " and 1 = 0" : " and workload_id in (:allowedWorkloads)";
        JdbcClient.StatementSpec spec = jdbcClient.sql("""
                select * from audit_export_job
                where export_id = :exportId and institution_id = :institutionId
                """ + workloadScope + (forUpdate ? " for update" : ""))
            .param("exportId", exportId).param("institutionId", institutionId);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            spec = spec.param("allowedWorkloads", allowedWorkloads);
        }
        return spec.query(JOB_MAPPER).optional()
            .orElseThrow(() -> new AuditExportException("AUDIT_EXPORT_NOT_FOUND", "Audit export not found"));
    }

    private JdbcClient.StatementSpec bindWorkQuery(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads,
        String principalId,
        AuditExportStatus status
    ) {
        statement = statement.param("institutionId", institutionId).param("principalId", principalId);
        statement = bindWorkloads(statement, allowedWorkloads);
        return status == null ? statement : statement.param("status", status.name());
    }

    private void appendWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) return;
        sql.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and workload_id in (:allowedWorkloads)");
    }

    private JdbcClient.StatementSpec bindWorkloads(
        JdbcClient.StatementSpec statement,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }

    private AuditExportJob loadById(String exportId) {
        return jdbcClient.sql("select * from audit_export_job where export_id = :exportId")
            .param("exportId", exportId).query(JOB_MAPPER).optional()
            .orElseThrow(() -> new AuditExportException("AUDIT_EXPORT_NOT_FOUND", "Audit export not found"));
    }

    private void expire(String exportId, String institutionId, OffsetDateTime now) {
        int changed = jdbcClient.sql("""
                update audit_export_job
                set status = 'EXPIRED', content = null, updated_at = :now, version = version + 1
                where export_id = :exportId and institution_id = :institutionId
                  and status = 'READY' and expires_at <= :now
                """)
            .param("now", now).param("exportId", exportId).param("institutionId", institutionId).update();
        if (changed == 1) {
            event(exportId, institutionId, "system:expiry", null, null, "EXPIRED",
                AuditExportStatus.READY, AuditExportStatus.EXPIRED, "AUDIT_EXPORT_EXPIRED", null, now);
            event(exportId, institutionId, "system:expiry", null, null, "DELETED",
                AuditExportStatus.EXPIRED, AuditExportStatus.EXPIRED, "AUDIT_EXPORT_CONTENT_DELETED", null, now);
        }
    }

    private void event(
        String exportId, String institutionId, String actorId, String requestId, String traceId,
        String action, AuditExportStatus from, AuditExportStatus to, String reasonCode,
        String reasonText, OffsetDateTime now
    ) {
        jdbcClient.sql("""
                insert into audit_export_event (
                    event_id, export_id, institution_id, actor_id, request_id, trace_id,
                    action, from_status, to_status, reason_code, reason_text, occurred_at
                ) values (
                    :eventId, :exportId, :institutionId, :actorId, :requestId, :traceId,
                    :action, :fromStatus, :toStatus, :reasonCode, :reasonText, :occurredAt
                )
                """)
            .param("eventId", "expevt_" + UUID.randomUUID()).param("exportId", exportId)
            .param("institutionId", institutionId).param("actorId", actorId)
            .param("requestId", requestId).param("traceId", traceId).param("action", action)
            .param("fromStatus", from == null ? null : from.name())
            .param("toStatus", to.name()).param("reasonCode", reasonCode).param("reasonText", reasonText)
            .param("occurredAt", now)
            .update();
    }

    private static AuditExportJob mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new AuditExportJob(
            rs.getString("export_id"), rs.getString("institution_id"), rs.getString("workload_id"),
            ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("execution_id"),
            rs.getString("report_type"), AuditExportFormat.valueOf(rs.getString("export_format")),
            AuditExportStatus.valueOf(rs.getString("status")), rs.getString("scope_digest"),
            rs.getString("requester_id"), rs.getString("approver_id"), rs.getString("request_reason"),
            rs.getString("approval_reason"), rs.getString("revoked_by"),
            rs.getObject("revoked_at", OffsetDateTime.class), rs.getString("revocation_reason"),
            rs.getString("idempotency_key"), rs.getString("scope_json"),
            (Integer) rs.getObject("row_count"), rs.getString("content_digest"),
            (Long) rs.getObject("content_size"), rs.getString("content_type"), rs.getString("file_name"),
            rs.getString("failure_code"), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("generated_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("downloaded_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version")
        );
    }

    private static AuditExportStatus nullableStatus(String value) {
        return value == null ? null : AuditExportStatus.valueOf(value);
    }

    private record ExpiredExport(String exportId, String institutionId) {
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
