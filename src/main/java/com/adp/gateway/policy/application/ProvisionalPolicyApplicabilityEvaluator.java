package com.adp.gateway.policy.application;

import java.util.Locale;

import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.CrosswalkMapping;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.RuntimeDataClassCrosswalkPort;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class ProvisionalPolicyApplicabilityEvaluator implements PolicyApplicabilityEvaluator {

    private final RuntimeDataClassCrosswalkPort crosswalkPort;

    public ProvisionalPolicyApplicabilityEvaluator(RuntimeDataClassCrosswalkPort crosswalkPort) {
        this.crosswalkPort = crosswalkPort;
    }

    @Override
    public ApplicabilityResult evaluate(PolicySnapshot snapshot, RuntimePolicyContext context) {
        if (snapshot.matchedRuleRefs().isEmpty()) {
            return ApplicabilityResult.INCOMPLETE;
        }
        PolicyApplicabilitySpec spec = snapshot.evaluation().applicabilitySpec();
        if (spec == null || spec.runtimeBinding() == null) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (spec.analysisStatus() == AnalysisStatus.REJECTED || spec.applicabilityStatus() == AnalysisStatus.REJECTED) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        if (spec.analysisStatus() != AnalysisStatus.VALIDATED || spec.applicabilityStatus() != AnalysisStatus.VALIDATED) {
            return ApplicabilityResult.INCOMPLETE;
        }
        RuntimeBinding binding = spec.runtimeBinding();
        if (!isResolvedMapping(binding.mappingStatus())) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (isUnresolved(binding.workloadId()) || isUnresolved(binding.purpose()) || isUnresolved(binding.runtimeDataClass())) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (!binding.workloadId().equals(context.workloadId()) || !binding.purpose().equals(context.purpose())) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        if (context.canonicalContextDigest() == null || context.runtimeDataClasses().isEmpty()) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (context.runtimeDataClasses().contains(DataClass.UNKNOWN)) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (!hasCrosswalkMatch(spec, context)) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (!spec.processingContexts().isEmpty() && context.processingContexts().isEmpty()) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (!hasProcessingContextMatch(spec, context)) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        if (!hasRuntimeDataClassMatch(binding, context)) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        return ApplicabilityResult.APPLICABLE;
    }

    private boolean isResolvedMapping(String mappingStatus) {
        String normalized = mappingStatus.toLowerCase(Locale.ROOT);
        return normalized.equals("mapped") || normalized.equals("resolved");
    }

    private boolean isUnresolved(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.equals("TBD") || normalized.equals("UNMAPPED") || normalized.equals("UNRESOLVED");
    }

    private boolean hasProcessingContextMatch(PolicyApplicabilitySpec spec, RuntimePolicyContext context) {
        if (spec.processingContexts().isEmpty()) {
            return true;
        }
        if (context.processingContexts().isEmpty()) {
            return false;
        }
        return spec.processingContexts().stream()
            .anyMatch(policyContext -> context.processingContexts().contains(policyContext));
    }

    private boolean hasRuntimeDataClassMatch(RuntimeBinding binding, RuntimePolicyContext context) {
        return context.runtimeDataClasses().stream()
            .map(DataClass::name)
            .anyMatch(runtimeDataClass -> runtimeDataClass.equals(binding.runtimeDataClass()));
    }

    private boolean hasCrosswalkMatch(PolicyApplicabilitySpec spec, RuntimePolicyContext context) {
        if (spec.regulatoryDataCategories().isEmpty()) {
            return true;
        }
        return spec.regulatoryDataCategories().stream()
            .map(crosswalkPort::resolve)
            .allMatch(mapping -> mapping
                .filter(this::isMapped)
                .filter(candidate -> context.runtimeDataClasses().stream()
                    .map(DataClass::name)
                    .anyMatch(runtimeDataClass -> runtimeDataClass.equals(candidate.runtimeDataClass())))
                .isPresent());
    }

    private boolean isMapped(CrosswalkMapping mapping) {
        return mapping.mappingStatus().equalsIgnoreCase("mapped");
    }
}
