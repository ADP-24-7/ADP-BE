package com.adp.gateway.auditexport.domain;

import java.time.OffsetDateTime;

public record AuditExportWorkSummary(
    String principalId,
    boolean approvalAvailable,
    PersonalWork personal,
    ApprovalWork approvals,
    Operations operations,
    OffsetDateTime generatedAt
) {
    public record PersonalWork(
        long pendingApproval,
        long approvedOrGenerating,
        long readyToDownload,
        long downloaded,
        long rejected,
        long failedOrExpired
    ) { }

    public record ApprovalWork(long pending, long waitingOver24Hours) { }

    public record Operations(
        long pendingApproval,
        Long oldestPendingAgeSeconds,
        long approvedLast24Hours,
        long rejectedLast24Hours,
        long generating,
        long ready,
        long failed,
        long expired
    ) { }
}
