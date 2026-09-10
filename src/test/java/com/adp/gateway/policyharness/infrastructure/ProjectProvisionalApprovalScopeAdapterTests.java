package com.adp.gateway.policyharness.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProjectProvisionalApprovalScopeAdapterTests {

    private final SubjectRefHasher subjectRefHasher = new SubjectRefHasher();
    private final AiModelProfileCatalog modelProfiles = new AiModelProfileCatalog(
        new ObjectMapper(),
        new CanonicalValueHasher()
    );
    private final ProjectProvisionalApprovalScopeAdapter approvals =
        new ProjectProvisionalApprovalScopeAdapter(subjectRefHasher, modelProfiles);
    private final OffsetDateTime requestStartedAt = OffsetDateTime.parse("2026-09-10T00:00:00Z");

    @Test
    void daProvenanceApprovalMatchesOnlyTheExactSubjectDigest() {
        var profile = modelProfiles.profiles().getFirst();
        var approval = approvals.load(
            modelProfiles.approvalReference(
                profile,
                AiEvaluationRunCatalog.DA_PROVENANCE_RUN_ID,
                AiEvaluationRunCatalog.DA_PROVENANCE_CASE_ID
            ),
            requestStartedAt
        );

        assertThat(approval.subjectScopeType()).isEqualTo("EXACT_DIGEST");
        assertThat(approval.subjectScopeDigest()).isEqualTo(
            subjectRefHasher.hash(ProjectProvisionalApprovalScopeAdapter.DA_PROVENANCE_SUBJECT)
        );
        assertThat(approval.subjectScopeDigest()).isNotEqualTo(
            subjectRefHasher.hash(new SubjectRef("customer", "da-customer-10833"))
        );
    }

    @Test
    void daProvenanceApprovalKeepsTheLegacyScopeAndModelBinding() {
        var profile = modelProfiles.profiles().getFirst();
        var legacy = approvals.load(modelProfiles.approvalReference(profile), requestStartedAt);
        var provenance = approvals.load(
            modelProfiles.approvalReference(
                profile,
                AiEvaluationRunCatalog.DA_PROVENANCE_RUN_ID,
                AiEvaluationRunCatalog.DA_PROVENANCE_CASE_ID
            ),
            requestStartedAt
        );

        assertThat(provenance.workloadId()).isEqualTo(legacy.workloadId());
        assertThat(provenance.purposeCode()).isEqualTo(legacy.purposeCode());
        assertThat(provenance.allowedFields()).isEqualTo(legacy.allowedFields());
        assertThat(provenance.allowedProcessingContexts()).isEqualTo(legacy.allowedProcessingContexts());
        assertThat(provenance.workloadPolicyVersion()).isEqualTo(legacy.workloadPolicyVersion());
        assertThat(provenance.workloadPolicySnapshotDigest()).isEqualTo(legacy.workloadPolicySnapshotDigest());
        assertThat(provenance.destinationProfileId()).isEqualTo(legacy.destinationProfileId());
        assertThat(provenance.destinationProfileVersion()).isEqualTo(legacy.destinationProfileVersion());
        assertThat(legacy.subjectScopeDigest()).isEqualTo(
            subjectRefHasher.hash(new SubjectRef("customer", "customer-100"))
        );
    }
}
