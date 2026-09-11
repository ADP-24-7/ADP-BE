package com.adp.gateway.runtime.domain;

import java.time.OffsetDateTime;

public record RuntimeExecutionTrace(
    String executionId,
    String requestId,
    String traceId,
    String idempotencyKey,
    String workloadId,
    String purposeCode,
    String subjectRefDigest,
    String providerProfileId,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    String destinationTenantId,
    String destinationRegion,
    String destinationRetentionPolicy,
    Boolean destinationTrainingUseAllowed,
    String institutionId,
    String approvalReference,
    String approvalVersion,
    String approvalScopeDigest,
    String approvalReuseStatus,
    String approvalReasonCodes,
    String policyLayers,
    String policyLayersDigest,
    String inputDigest,
    String canonicalContextDigest,
    String runtimeContextDigest,
    String policyVersion,
    String snapshotDigest,
    String decisionId,
    String policyAction,
    String policyReasonCodes,
    String policyRequirementRefs,
    String policyEvidenceRefs,
    String finalAction,
    String transformExecutionId,
    String transformStatus,
    String transformOutputDigest,
    String outboundPayloadId,
    String outboundCandidateDigest,
    String outboundGuardStatus,
    String connectorExecutionId,
    String connectorStatus,
    String responseGuardStatus,
    String responseGuardReasonCodes,
    String responseFindingTypes,
    String requestedFields,
    String requestedFieldsDigest,
    Integer requestedFieldCount,
    String retrievedFields,
    String retrievedFieldsDigest,
    Integer retrievedFieldCount,
    String transformedFields,
    String transformedFieldsDigest,
    Integer transformedFieldCount,
    String releasedFields,
    String releasedFieldsDigest,
    Integer releasedFieldCount,
    String providerRequestId,
    String providerRequestDigest,
    String providerResponseDigest,
    String authorizationStatus,
    String controlledDeliveryStatus,
    String controlledDeliveryResponseDigest,
    String controlledDeliveryReasonCode,
    OffsetDateTime controlledDeliveredAt,
    String aiModelProfileId,
    String aiModelProfileVersion,
    String aiModelProfileDigest,
    String aiProviderModelId,
    String aiProviderModelVersion,
    String aiConnectionProfileId,
    Integer aiMaxTokens,
    Double aiTemperature,
    String aiSamplingProfileVersion,
    String aiEvaluationRunId,
    String aiEvaluationRunVersion,
    String aiEvalCaseId,
    String aiEvaluationContractDigest,
    String aiExpectedInputDigest,
    String aiActualInputDigest,
    String aiDatasetId,
    String aiDatasetVersion,
    String aiDatasetDigest,
    String aiPolicySnapshotDigest,
    String aiDestinationProfileDigest,
    String aiMeasurementType,
    Long aiFullResponseLatencyMillis,
    Long aiAttemptElapsedMillis,
    Integer aiInputTokens,
    Integer aiOutputTokens,
    Integer aiTotalTokens,
    String aiTokenUsageStatus,
    String aiProviderStatus,
    String aiErrorCategory,
    Integer aiProviderHttpStatus,
    String aiEvidenceStatus,
    Long aiInitialRuntimeLatencyMillis,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static RuntimeExecutionTrace received(
        String executionId,
        String requestId,
        String traceId,
        String idempotencyKey,
        String workloadId,
        String purposeCode,
        String subjectRefDigest,
        String destinationProfileId,
        String institutionId,
        String approvalReference,
        String inputDigest,
        OffsetDateTime receivedAt
    ) {
        return new RuntimeExecutionTrace(
            executionId, // executionId
            requestId, // requestId
            traceId, // traceId
            idempotencyKey, // idempotencyKey
            workloadId, // workloadId
            purposeCode, // purposeCode
            subjectRefDigest, // subjectRefDigest
            null, // providerProfileId
            destinationProfileId, // destinationProfileId
            null, // destinationProfileVersion
            null, // destinationProfileDigest
            null, // destinationTenantId
            null, // destinationRegion
            null, // destinationRetentionPolicy
            null, // destinationTrainingUseAllowed
            institutionId, // institutionId
            approvalReference, // approvalReference
            null, // approvalVersion
            null, // approvalScopeDigest
            null, // approvalReuseStatus
            null, // approvalReasonCodes
            null, // policyLayers
            null, // policyLayersDigest
            inputDigest, // inputDigest
            null,
            null,
            null,
            null,
            null,
            null, // policyAction
            null, // policyReasonCodes
            null, // policyRequirementRefs
            null, // policyEvidenceRefs
            null,
            null,
            null,
            null,
            null,
            null,
            null, // responseFindingTypes
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            RuntimeExecutionStatus.RECEIVED.name(),
            receivedAt,
            receivedAt
        );
    }
}
