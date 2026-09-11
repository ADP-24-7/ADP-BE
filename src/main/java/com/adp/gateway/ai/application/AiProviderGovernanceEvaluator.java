package com.adp.gateway.ai.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.domain.AiProviderGovernanceContract;
import com.adp.gateway.ai.domain.AiProviderGovernanceDecision;
import org.springframework.stereotype.Component;

@Component
public class AiProviderGovernanceEvaluator {

    public AiProviderGovernanceDecision evaluate(
        AiProviderGovernanceContract contract,
        String requestedProviderConnectionProfileId,
        String requestedModelProfileId,
        String requestedWorkloadId,
        String requestedPurposeCode,
        String requestedDestinationProfileId,
        String requestedDestinationDigest,
        String requestedRegion,
        String resolvedRegion,
        String providerRetentionMode,
        Integer providerRetentionDays,
        String retentionVerificationStatus,
        List<String> providerReusePurposes
    ) {
        List<String> reasons = new ArrayList<>();
        boolean providerPass = contract.providerConnectionProfileId().equals(requestedProviderConnectionProfileId);
        if (!providerPass) reasons.add("AI_PROVIDER_NOT_APPROVED");
        boolean modelPass = requestedModelProfileId != null
            && contract.modelProfileIds().contains(requestedModelProfileId);
        if (!modelPass) reasons.add("AI_MODEL_NOT_APPROVED");
        boolean workloadPass = contract.workloadId().equals(requestedWorkloadId)
            && contract.purposeCode().equals(requestedPurposeCode)
            && requestedDestinationProfileId != null
            && requestedDestinationDigest != null
            && contract.destinationProfileIds().contains(requestedDestinationProfileId)
            && contract.destinationProfileDigests().contains(requestedDestinationDigest);
        if (!workloadPass) reasons.add("AI_WORKLOAD_OR_DESTINATION_BINDING_MISMATCH");

        String regionReason = null;
        if (contract.regionRequired() && (requestedRegion == null || requestedRegion.isBlank()
            || resolvedRegion == null || resolvedRegion.isBlank() || "UNRESOLVED".equals(resolvedRegion))) {
            regionReason = "PROVIDER_REGION_REQUIRED";
        } else if (!contract.allowedRegions().contains(requestedRegion)) {
            regionReason = "PROVIDER_REGION_NOT_ALLOWED";
        } else if (!requestedRegion.equals(resolvedRegion)) {
            regionReason = "PROVIDER_REGION_MISMATCH";
        }
        if (regionReason != null) reasons.add(regionReason);

        String retentionReason = null;
        if (!"VERIFIED".equals(retentionVerificationStatus)
            || providerRetentionMode == null || "UNSPECIFIED".equals(providerRetentionMode)) {
            retentionReason = "RETENTION_UNVERIFIED";
        } else if (!contract.approvedRetentionMode().equals(providerRetentionMode)) {
            retentionReason = "RETENTION_POLICY_MISMATCH";
        } else if (contract.maximumRetentionDays() != null
            && (providerRetentionDays == null || providerRetentionDays > contract.maximumRetentionDays())) {
            retentionReason = "RETENTION_LIMIT_EXCEEDED";
        }
        if (retentionReason != null) reasons.add(retentionReason);

        Set<String> allowedReuse = Set.copyOf(contract.allowedReusePurposes());
        List<String> observedReuse = providerReusePurposes == null ? List.of() : providerReusePurposes;
        List<String> unauthorizedReuse = observedReuse.stream()
            .filter(purpose -> !allowedReuse.contains(purpose)).toList();
        String reuseReason = null;
        if (observedReuse.isEmpty()) {
            reuseReason = "REUSE_SCOPE_MISMATCH";
        } else if (unauthorizedReuse.contains("MODEL_TRAINING") || unauthorizedReuse.contains("AI_MODEL_IMPROVEMENT")) {
            reuseReason = "MODEL_TRAINING_NOT_ALLOWED";
        } else if (!unauthorizedReuse.isEmpty()) {
            reuseReason = "REUSE_SCOPE_MISMATCH";
        }
        if (reuseReason != null) reasons.add(reuseReason);

        return new AiProviderGovernanceDecision(
            reasons.isEmpty() ? "PASS" : "BLOCK",
            providerPass ? "PASS" : "BLOCK",
            modelPass ? "PASS" : "BLOCK",
            workloadPass ? "PASS" : "BLOCK",
            regionReason == null ? "PASS" : "BLOCK", regionReason,
            retentionReason == null ? "PASS" : "BLOCK", retentionReason,
            reuseReason == null ? "PASS" : "BLOCK", reuseReason,
            reasons
        );
    }
}
