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
import com.adp.gateway.digitalasset.domain.DigitalAssetPolicyProfile;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.ArtifactReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class DigitalAssetPolicyGate implements ExecutionPackPolicyGate {

    private final DigitalAssetPolicyProfilePort profilePort;
    private final DigitalAssetComplianceContextPort complianceContextPort;

    public DigitalAssetPolicyGate(
        DigitalAssetPolicyProfilePort profilePort,
        DigitalAssetComplianceContextPort complianceContextPort
    ) {
        this.profilePort = profilePort;
        this.complianceContextPort = complianceContextPort;
    }

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
        DigitalAssetPolicyProfile profile = profilePort.load(
            destinationProfile.destinationProfileId(), requestStartedAt
        );
        var compliance = complianceContextPort.load(
            text(context, "customerId"), text(context, "accountId"), text(context, "walletAddress")
        );
        List<ReasonCode> reasons = reasons(context, destinationProfile, profile, compliance, requestStartedAt);
        FinalAction profileAction = reasons.contains(ReasonCode.DIGITAL_ASSET_POLICY_PROFILE_INVALID)
            ? FinalAction.BLOCK
            : reasons.isEmpty() ? FinalAction.ALLOW : FinalAction.REVIEW;
        FinalAction finalAction = profileAction.isAtLeastAsRestrictiveAs(baselineDecision.finalAction())
            ? profileAction : baselineDecision.finalAction();

        List<ReasonCode> combinedReasons = new ArrayList<>(baselineDecision.runtimeReasonCodes());
        reasons.stream().filter(reason -> !combinedReasons.contains(reason)).forEach(combinedReasons::add);
        List<ArtifactReference> policyRefs = new ArrayList<>(baselineDecision.matchedPolicyRefs());
        policyRefs.add(new ArtifactReference(profile.profileId(), "digital_asset_policy_profile", profile.version()));
        String identity = String.join("|",
            baselineDecision.decisionId(), profile.digest(), finalAction.name(),
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
            supportedPack(), profile.profileId(), profile.version(), profile.digest(),
            baselineDecision.finalAction(), profileAction, finalAction, reasons,
            compliance.sourceSystem(), compliance.assertionVersion(), compliance.evidenceDigest(), decision
        );
    }

    private List<ReasonCode> reasons(
        CanonicalContext context,
        DestinationProfile destinationProfile,
        DigitalAssetPolicyProfile profile,
        com.adp.gateway.digitalasset.domain.DigitalAssetComplianceContext compliance,
        OffsetDateTime requestStartedAt
    ) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (!profile.destinationProfileId().equals(destinationProfile.destinationProfileId())
            || !profile.isEffectiveAt(requestStartedAt)
            || !profile.complianceSourceSystem().equals(compliance.sourceSystem())
            || !profile.complianceAssertionVersion().equals(compliance.assertionVersion())
            || !text(context, "kycStatus").equals(compliance.kycStatus())
            || !text(context, "amlStatus").equals(compliance.amlStatus())
            || !Boolean.valueOf(text(context, "walletVerified")).equals(compliance.walletVerified())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_POLICY_PROFILE_INVALID);
            return reasons;
        }
        if (!profile.allowedKycStatuses().contains(text(context, "kycStatus"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_KYC_REVIEW_REQUIRED);
        }
        if (!profile.allowedAmlStatuses().contains(text(context, "amlStatus"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_AML_REVIEW_REQUIRED);
        }
        if (profile.walletVerificationRequired() && !Boolean.TRUE.equals(value(context, "walletVerified"))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_WALLET_REVIEW_REQUIRED);
        }
        if (new BigDecimal(text(context, "amount")).compareTo(profile.amountLimit()) > 0) {
            reasons.add(ReasonCode.DIGITAL_ASSET_AMOUNT_LIMIT_REVIEW_REQUIRED);
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
}
