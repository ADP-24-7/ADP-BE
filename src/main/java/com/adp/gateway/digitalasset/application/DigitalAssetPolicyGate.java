package com.adp.gateway.digitalasset.application;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.application.ExecutionPackPolicyGate;
import com.adp.gateway.decision.domain.ExecutionPackPolicyEvaluation;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.ArtifactReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class DigitalAssetPolicyGate implements ExecutionPackPolicyGate {

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ExecutionPackPolicyEvaluation evaluate(
        CanonicalContext context,
        DestinationProfile destinationProfile,
        RuntimeDecision baselineDecision,
        OffsetDateTime requestStartedAt
    ) {
        List<ReasonCode> reasons = bindingReasons(context);
        FinalAction profileAction = reasons.isEmpty() ? FinalAction.ALLOW : FinalAction.BLOCK;
        FinalAction finalAction = profileAction.isAtLeastAsRestrictiveAs(baselineDecision.finalAction())
            ? profileAction : baselineDecision.finalAction();

        List<ReasonCode> combinedReasons = new ArrayList<>(baselineDecision.runtimeReasonCodes());
        reasons.stream().filter(reason -> !combinedReasons.contains(reason)).forEach(combinedReasons::add);
        List<ArtifactReference> policyRefs = new ArrayList<>(baselineDecision.matchedPolicyRefs());
        policyRefs.add(new ArtifactReference(
            metadata(context, "approvedTransactionId"),
            "approved_transaction_snapshot",
            metadata(context, "approvedTransactionVersion")
        ));
        String identity = String.join("|",
            baselineDecision.decisionId(), metadata(context, "approvedTransactionDigest"), finalAction.name(),
            reasons.stream().map(Enum::name).sorted().reduce((left, right) -> left + "," + right).orElse("NONE")
        );
        RuntimeDecision decision = new RuntimeDecision(
            "dec_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
            baselineDecision.policyAction(), finalAction, combinedReasons,
            baselineDecision.authorizationResult(), baselineDecision.applicabilityResult(), policyRefs,
            baselineDecision.matchedRuleRefs(), baselineDecision.requirementRefs(), baselineDecision.evidenceRefs(),
            baselineDecision.requiredControls(), baselineDecision.validationArtifactRefs(),
            baselineDecision.policyVersion(), baselineDecision.snapshotDigest(),
            baselineDecision.runtimeContextDigest(), baselineDecision.sourcePolicyEvaluationArtifactRef()
        );
        return new ExecutionPackPolicyEvaluation(
            supportedPack(), metadata(context, "approvedTransactionId"),
            metadata(context, "approvedTransactionVersion"), metadata(context, "approvedTransactionDigest"),
            baselineDecision.finalAction(), profileAction, finalAction, reasons,
            null, null, null, decision
        );
    }

    private List<ReasonCode> bindingReasons(CanonicalContext context) {
        String value = metadata(context, "approvedBindingReasonCodes");
        if ("NONE".equals(value)) {
            return List.of();
        }
        try {
            return java.util.Arrays.stream(value.split(","))
                .map(ReasonCode::valueOf)
                .toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Digital asset binding reason metadata is invalid", exception);
        }
    }

    private String metadata(CanonicalContext context, String name) {
        String value = context.trustedMetadata().get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Digital asset trusted metadata is missing");
        }
        return value;
    }
}
