package com.adp.gateway.runtime.api;

import java.util.Arrays;
import java.util.List;

import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public record RuntimeExecutionEvidenceResponse(
    String institutionId,
    String approvalReference,
    String approvalVersion,
    String approvalScopeDigest,
    String approvalReuseStatus,
    List<String> approvalReasonCodes,
    List<String> policyLayers,
    String policyLayersDigest,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    String destinationTenantId,
    String destinationRegion,
    String destinationRetentionPolicy,
    Boolean destinationTrainingUseAllowed,
    FieldSetEvidence requested,
    FieldSetEvidence retrieved,
    FieldSetEvidence transformed,
    FieldSetEvidence released,
    String providerRequestId,
    String providerRequestDigest,
    String providerResponseDigest,
    String responseGuardStatus,
    List<String> responseGuardReasonCodes
) {

    public static RuntimeExecutionEvidenceResponse from(RuntimeExecutionTrace trace) {
        return new RuntimeExecutionEvidenceResponse(
            trace.institutionId(),
            trace.approvalReference(),
            trace.approvalVersion(),
            trace.approvalScopeDigest(),
            trace.approvalReuseStatus(),
            values(trace.approvalReasonCodes()),
            values(trace.policyLayers()),
            trace.policyLayersDigest(),
            trace.destinationProfileId(),
            trace.destinationProfileVersion(),
            trace.destinationProfileDigest(),
            trace.destinationTenantId(),
            trace.destinationRegion(),
            trace.destinationRetentionPolicy(),
            trace.destinationTrainingUseAllowed(),
            new FieldSetEvidence(values(trace.requestedFields()), trace.requestedFieldsDigest(), trace.requestedFieldCount()),
            new FieldSetEvidence(values(trace.retrievedFields()), trace.retrievedFieldsDigest(), trace.retrievedFieldCount()),
            new FieldSetEvidence(values(trace.transformedFields()), trace.transformedFieldsDigest(), trace.transformedFieldCount()),
            new FieldSetEvidence(values(trace.releasedFields()), trace.releasedFieldsDigest(), trace.releasedFieldCount()),
            trace.providerRequestId(),
            trace.providerRequestDigest(),
            trace.providerResponseDigest(),
            trace.responseGuardStatus(),
            values(trace.responseGuardReasonCodes())
        );
    }

    private static List<String> values(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .filter(reason -> !reason.isBlank())
            .toList();
    }

    public record FieldSetEvidence(List<String> fields, String digest, Integer count) {

        public FieldSetEvidence {
            fields = List.copyOf(fields);
        }
    }
}
