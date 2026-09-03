package com.adp.gateway.audit.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionEvidencePack(
    String schemaVersion,
    String evidenceDigest,
    String executionId,
    String requestId,
    String traceId,
    String institutionId,
    String workloadId,
    String purposeCode,
    String runtimeStatus,
    String authorizationStatus,
    PolicyEvidence policy,
    DataEvidence data,
    EgressEvidence egress,
    RecoveryEvidence recovery,
    AuditEvidence audit,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public record PolicyEvidence(
        String approvalReference,
        String approvalVersion,
        String approvalScopeDigest,
        String policyVersion,
        String snapshotDigest,
        String decisionId,
        String finalAction
    ) {
    }

    public record DataEvidence(
        String subjectRefDigest,
        String inputDigest,
        String canonicalContextDigest,
        String runtimeContextDigest,
        Integer requestedFieldCount,
        String requestedFieldsDigest,
        Integer retrievedFieldCount,
        String retrievedFieldsDigest,
        Integer transformedFieldCount,
        String transformedFieldsDigest,
        Integer releasedFieldCount,
        String releasedFieldsDigest
    ) {
    }

    public record EgressEvidence(
        String destinationProfileId,
        String destinationProfileVersion,
        String destinationProfileDigest,
        String outboundCandidateDigest,
        String outboundGuardStatus,
        String connectorExecutionId,
        String connectorStatus,
        String providerRequestDigest,
        String providerResponseDigest,
        String responseGuardStatus,
        String controlledDeliveryStatus,
        String controlledDeliveryResponseDigest
    ) {
    }

    public record RecoveryEvidence(
        String recoveryStatus,
        String retryDisposition,
        Integer attemptCount,
        Integer maxAttempts,
        String lastObservedExternalStatus,
        OffsetDateTime lastStatusQueriedAt,
        String statusQueryEvidenceDigest,
        String lastErrorCode
    ) {
    }

    public record AuditEvidence(String auditId, String reasonCode, List<String> evidenceRefs) {
        public AuditEvidence {
            evidenceRefs = List.copyOf(evidenceRefs);
        }
    }
}
