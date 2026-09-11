package com.adp.gateway.ai.api;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiTransformGovernanceProfileResponse(
    String evaluationRunId,
    String workloadId,
    String workloadName,
    String businessDomain,
    String purposeCode,
    String purposeDescription,
    String subjectScope,
    String actionType,
    String requesterRole,
    String e2HandoffDigest,
    String requirementVersion,
    String e2ValidationStatus,
    String providerGovernanceStatus,
    String externalExecutionStatus,
    boolean providerCallAuthorized,
    List<FieldControl> fieldControls,
    List<RequirementEnforcementGap> requirementEnforcementGaps
) {
    public AiTransformGovernanceProfileResponse {
        fieldControls = List.copyOf(fieldControls);
        requirementEnforcementGaps = List.copyOf(requirementEnforcementGaps);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FieldControl(
        String fieldName,
        String classification,
        String businessNeed,
        String fieldRequirement,
        List<String> transformIntents,
        List<String> utilityRequirements,
        List<String> candidateTransformMethods,
        List<String> prohibitedTransformMethods,
        String currentRuntimeMethod,
        String requiredTransformMethod,
        boolean currentRuntimeRequirementMatch,
        boolean externalReleaseAllowed,
        String applicability,
        String requirementStatus,
        String evidenceRef
    ) {
        public FieldControl {
            transformIntents = List.copyOf(transformIntents);
            utilityRequirements = List.copyOf(utilityRequirements);
            candidateTransformMethods = List.copyOf(candidateTransformMethods);
            prohibitedTransformMethods = List.copyOf(prohibitedTransformMethods);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RequirementEnforcementGap(
        String fieldName,
        String currentRuntimeMethod,
        String requiredTransformMethod,
        String reason,
        String requiredAction
    ) {}
}
