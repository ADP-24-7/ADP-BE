package com.adp.gateway.digitalasset.application;

import java.math.BigDecimal;
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
        List<ReasonCode> reasons = reasons(context, destinationProfile, requestStartedAt);
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

    private List<ReasonCode> reasons(
        CanonicalContext context,
        DestinationProfile destinationProfile,
        OffsetDateTime requestStartedAt
    ) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (!destinationProfile.destinationProfileId().equals(
            metadata(context, "approvedDestinationProfileId")
        )) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_PROFILE_MISMATCH);
        }
        if (!text(context, "assetId").equals(metadata(context, "approvedAssetId"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_ASSET_MISMATCH);
        }
        if (new BigDecimal(text(context, "amount")).compareTo(
            new BigDecimal(metadata(context, "approvedMaxAmount"))
        ) > 0) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED);
        }
        if (!text(context, "walletAddress").equals(metadata(context, "approvedDestination"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH);
        }
        if (!text(context, "beneficiaryReference").equals(metadata(context, "approvedBeneficiaryReference"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_BENEFICIARY_MISMATCH);
        }
        OffsetDateTime approvedFrom = OffsetDateTime.parse(metadata(context, "approvedFrom"));
        OffsetDateTime approvedUntil = OffsetDateTime.parse(metadata(context, "approvedUntil"));
        if (requestStartedAt.isBefore(approvedFrom) || requestStartedAt.isAfter(approvedUntil)) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_PERIOD_VIOLATION);
        }
        return List.copyOf(reasons);
    }

    private String text(CanonicalContext context, String fieldName) {
        return String.valueOf(value(context, fieldName));
    }

    private Object value(CanonicalContext context, String fieldName) {
        return context.fields().stream()
            .filter(field -> field.path().equals("$.input." + fieldName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Digital asset policy field is missing"))
            .value();
    }

    private String metadata(CanonicalContext context, String name) {
        String value = context.trustedMetadata().get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Digital asset trusted metadata is missing");
        }
        return value;
    }
}
