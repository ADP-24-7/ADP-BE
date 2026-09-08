package com.adp.gateway.policy.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyShadowDiffField;
import com.adp.gateway.policy.domain.PolicyShadowEvidence;
import com.adp.gateway.policy.domain.PolicyShadowOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyShadowService {
    private final PolicyLifecyclePersistence lifecyclePersistence;
    private final PolicyShadowEvaluator evaluator;
    private final PolicyShadowEvidencePersistence evidencePersistence;
    private final CanonicalValueHasher hasher;
    private final Clock clock;

    public PolicyShadowService(
        PolicyLifecyclePersistence lifecyclePersistence,
        PolicyShadowEvaluator evaluator,
        PolicyShadowEvidencePersistence evidencePersistence,
        CanonicalValueHasher hasher,
        Clock clock
    ) {
        this.lifecyclePersistence = lifecyclePersistence;
        this.evaluator = evaluator;
        this.evidencePersistence = evidencePersistence;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public PolicyShadowEvidence evaluate(
        AuthPrincipal principal,
        String candidateArtifactId,
        String candidateArtifactVersion,
        String evaluationCaseId
    ) {
        requireOperator(principal);
        if (blank(principal.institutionId()) || blank(evaluationCaseId)) {
            throw rejected("POLICY_SHADOW_REQUEST_INVALID");
        }
        var candidate = lifecyclePersistence.load(
            principal.institutionId(), principal.workloadIds(), candidateArtifactId, candidateArtifactVersion
        );
        if (candidate.lifecycleStage() != PolicyLifecycleStage.REPLAY) {
            throw rejected("POLICY_SHADOW_CANDIDATE_NOT_REPLAYED");
        }
        var baseline = lifecyclePersistence.loadActive(
            principal.institutionId(), principal.workloadIds(), candidate.policyLayer(), candidate.executionPack(),
            candidate.workloadId(), candidate.purposeCode()
        );
        if (baseline.artifactId().equals(candidate.artifactId())
            && baseline.artifactVersion().equals(candidate.artifactVersion())) {
            throw rejected("POLICY_SHADOW_BASELINE_INVALID");
        }

        PolicyShadowOutcome baselineOutcome = evaluator.evaluate(baseline, evaluationCaseId);
        PolicyShadowOutcome candidateOutcome = evaluator.evaluate(candidate, evaluationCaseId);
        validateComparable(baselineOutcome, candidateOutcome, evaluationCaseId);
        List<PolicyShadowDiffField> diffFields = diff(baselineOutcome, candidateOutcome);
        lifecyclePersistence.revalidateShadowInputs(candidate, baseline, principal.workloadIds());
        String identity = String.join("|",
            principal.institutionId(), baseline.artifactId(), baseline.artifactVersion(), baseline.artifactDigest(),
            String.valueOf(baseline.revision()),
            candidate.artifactId(), candidate.artifactVersion(),
            String.valueOf(candidate.revision()), evaluationCaseId, baselineOutcome.inputDigest()
        );
        OffsetDateTime now = OffsetDateTime.now(clock);
        return evidencePersistence.save(new PolicyShadowEvidence(
            "shadow_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
            principal.institutionId(), candidate.workloadId(), candidate.purposeCode(),
            baseline.artifactId(), baseline.artifactVersion(), baseline.artifactDigest(),
            candidate.artifactId(), candidate.artifactVersion(), candidate.artifactDigest(), candidate.revision(),
            baselineOutcome.evaluationCaseId(), baselineOutcome.evaluationCaseVersion(), baselineOutcome.inputDigest(),
            digest(baselineOutcome), digest(candidateOutcome), diffFields,
            diffFields.isEmpty() ? "MATCH" : "DIFF", principal.principalId(), now
        ));
    }

    private void validateComparable(
        PolicyShadowOutcome baseline,
        PolicyShadowOutcome candidate,
        String requestedCaseId
    ) {
        if (!requestedCaseId.equals(baseline.evaluationCaseId())
            || !baseline.evaluationCaseId().equals(candidate.evaluationCaseId())
            || !baseline.evaluationCaseVersion().equals(candidate.evaluationCaseVersion())
            || !baseline.inputDigest().equals(candidate.inputDigest())) {
            throw rejected("POLICY_SHADOW_INPUT_MISMATCH");
        }
    }

    private List<PolicyShadowDiffField> diff(PolicyShadowOutcome baseline, PolicyShadowOutcome candidate) {
        List<PolicyShadowDiffField> fields = new ArrayList<>();
        add(fields, PolicyShadowDiffField.FINAL_ACTION, baseline.finalAction(), candidate.finalAction());
        add(fields, PolicyShadowDiffField.REASON_CODES, baseline.reasonCodes(), candidate.reasonCodes());
        add(fields, PolicyShadowDiffField.REQUIRED_CONTROLS,
            baseline.requiredControls(), candidate.requiredControls());
        add(fields, PolicyShadowDiffField.TRANSFORM_STRATEGY,
            baseline.transformStrategy(), candidate.transformStrategy());
        add(fields, PolicyShadowDiffField.DESTINATION_PROFILE,
            baseline.destinationProfileId(), candidate.destinationProfileId());
        return List.copyOf(fields);
    }

    private void add(List<PolicyShadowDiffField> fields, PolicyShadowDiffField field, Object left, Object right) {
        if (!java.util.Objects.equals(left, right)) {
            fields.add(field);
        }
    }

    private String digest(PolicyShadowOutcome outcome) {
        return hasher.hash(String.join("|",
            outcome.evaluationCaseId(), outcome.evaluationCaseVersion(), outcome.inputDigest(),
            outcome.finalAction().name(), String.join(",", outcome.reasonCodes()),
            String.join(",", outcome.requiredControls()), outcome.transformStrategy(), outcome.destinationProfileId()
        ));
    }

    private void requireOperator(AuthPrincipal principal) {
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)) {
            throw rejected("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > 120;
    }

    private PolicyLifecycleException rejected(String reason) {
        return new PolicyLifecycleException(reason);
    }
}
