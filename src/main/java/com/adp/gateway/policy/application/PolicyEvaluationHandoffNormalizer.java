package com.adp.gateway.policy.application;

import java.util.List;
import java.util.Locale;

import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluationHandoffNormalizer {

    public NormalizedPolicyEvaluation normalize(
        PolicyEvaluationHandoffArtifact artifact,
        PolicyAction runtimePolicyAction
    ) {
        PolicyEvaluation evaluation = new PolicyEvaluation(
            refs(artifact.matchedPolicyRefs()),
            refs(artifact.matchedRuleRefs()),
            refs(artifact.requirementRefs()),
            refs(artifact.evidenceRefs()),
            runtimePolicyAction,
            refs(artifact.requiredControls()),
            refs(artifact.validationArtifactRefs()),
            new PolicyApplicabilitySpec(
                status(artifact.analysisStatus()),
                status(artifact.applicability().status()),
                artifact.applicability().scope(),
                artifact.applicability().limitations(),
                artifact.processingContexts(),
                artifact.regulatoryDataCategories(),
                new RuntimeBinding(
                    artifact.runtimeBinding().mappingStatus(),
                    artifact.runtimeBinding().runtimeDataClass(),
                    artifact.runtimeBinding().workloadId(),
                    artifact.runtimeBinding().purpose(),
                    artifact.runtimeBinding().bindingRef()
                )
            )
        );
        SourcePolicyEvaluationArtifactRef source = new SourcePolicyEvaluationArtifactRef(
            artifact.artifactId(),
            artifact.artifactVersion(),
            new ArtifactDigest(artifact.digest().algorithm(), artifact.digest().value())
        );
        return new NormalizedPolicyEvaluation(source, artifact.policyAction(), evaluation);
    }

    private List<ArtifactReference> refs(List<HandoffReference> refs) {
        return refs.stream()
            .map(ref -> new ArtifactReference(ref.refId(), ref.refType(), ref.version()))
            .toList();
    }

    private AnalysisStatus status(String status) {
        return AnalysisStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
