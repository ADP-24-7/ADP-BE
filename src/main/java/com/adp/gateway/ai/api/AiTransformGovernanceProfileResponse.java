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
    String e3ProfileVersion,
    String e3ProfileDigest,
    String e3ValidationStatus,
    String activationStatus,
    String e2ValidationStatus,
    String providerGovernanceStatus,
    String externalExecutionStatus,
    boolean providerCallAuthorized,
    ProviderGovernance providerGovernance,
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
        String validatedMethod,
        String privacyResult,
        String utilityResult,
        String relationResult,
        String exactResult,
        String runtimeCompatibility,
        String destinationCompatibility,
        List<String> methodEvidenceIds,
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
            methodEvidenceIds = List.copyOf(methodEvidenceIds);
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

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProviderGovernance(
        String providerConnectionProfileId,
        List<String> modelProfileIds,
        List<String> allowedRegions,
        String requestedRegion,
        String resolvedRegion,
        String regionDecision,
        String regionReasonCode,
        String approvedRetentionMode,
        Integer maximumRetentionDays,
        String providerConfiguredRetentionMode,
        Integer providerRetentionDays,
        String retentionVerificationStatus,
        String retentionDecision,
        String retentionReasonCode,
        List<String> allowedReusePurposes,
        List<String> providerReusePurposes,
        String reuseDecision,
        String reuseReasonCode,
        String governanceDecision,
        List<String> reasonCodes,
        String contractVersion,
        String contractDigest,
        String activationStatus
    ) {
        public ProviderGovernance {
            modelProfileIds = List.copyOf(modelProfileIds);
            allowedRegions = List.copyOf(allowedRegions);
            allowedReusePurposes = List.copyOf(allowedReusePurposes);
            providerReusePurposes = List.copyOf(providerReusePurposes);
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
