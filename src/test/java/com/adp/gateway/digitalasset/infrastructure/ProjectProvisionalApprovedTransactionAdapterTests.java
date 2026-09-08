package com.adp.gateway.digitalasset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.digitalasset.application.ApprovedTransactionLookup;
import com.adp.gateway.digitalasset.domain.ApprovedTransactionReference;
import org.junit.jupiter.api.Test;

class ProjectProvisionalApprovedTransactionAdapterTests {

    private final SubjectRefHasher subjectRefHasher = new SubjectRefHasher();
    private final ProjectProvisionalApprovedTransactionAdapter adapter =
        new ProjectProvisionalApprovedTransactionAdapter(subjectRefHasher, new CanonicalValueHasher());

    @Test
    void rejectsAValidReferenceOutsideItsInstitutionOrSubjectScope() {
        String subjectDigest = subjectRefHasher.hash(new SubjectRef("customer", "customer-100"));

        assertThat(adapter.find(lookup("institution-other", subjectDigest))).isEmpty();
        assertThat(adapter.find(lookup("institution_local", "subject-other"))).isEmpty();
    }

    @Test
    void returnsServerOwnedTermsForAValidScopedReference() {
        String subjectDigest = subjectRefHasher.hash(new SubjectRef("customer", "customer-100"));

        var snapshot = adapter.find(lookup("institution_local", subjectDigest)).orElseThrow();

        assertThat(snapshot.approvedAssetId()).isEqualTo("asset-krw-token-001");
        assertThat(snapshot.approvedDestinationProfileId()).isEqualTo("dest_mock_asset_platform_v1");
        assertThat(snapshot.approvedDestination()).isEqualTo("wallet-test-001");
        assertThat(snapshot.approvedBeneficiaryReference()).isEqualTo("beneficiary-local-001");
    }

    private ApprovedTransactionLookup lookup(String institutionId, String subjectDigest) {
        return new ApprovedTransactionLookup(
            new ApprovedTransactionReference(ProjectProvisionalApprovedTransactionAdapter.DEFAULT_REFERENCE),
            institutionId, subjectDigest, "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE"
        );
    }
}
