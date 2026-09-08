package com.adp.gateway.digitalasset.infrastructure;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.digitalasset.application.ApprovedTransactionLookup;
import com.adp.gateway.digitalasset.application.ApprovedTransactionPort;
import com.adp.gateway.digitalasset.domain.ApprovedTransaction;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalApprovedTransactionAdapter implements ApprovedTransactionPort {
    public static final String BENEFICIARY_REFERENCE = "beneficiary-local-001";
    public static final String DEFAULT_REFERENCE = "approved-tx-local-001";
    private static final String INSTITUTION_ID = "institution_local";
    private static final String WORKLOAD_ID = DigitalAssetCanonicalContract.BASELINE_WORKLOAD_ID;
    private static final String PURPOSE = DigitalAssetCanonicalContract.BASELINE_PURPOSE_CODE;
    private static final String POLICY_SNAPSHOT_ID = "approved-policy-local-v1";
    private static final String DESTINATION_PROFILE_ID =
        DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_ID;
    private static final String DESTINATION = "wallet-test-001";
    private static final Map<String, String> APPROVED_ASSETS = Map.of(
        DEFAULT_REFERENCE, "asset-krw-token-001",
        "approved-tx-asset-settling", "asset-settling",
        "approved-tx-asset-critical-mismatch", "asset-critical-mismatch",
        "approved-tx-asset-mismatch", "asset-mismatch",
        "approved-tx-asset-correlation-mismatch", "asset-correlation-mismatch",
        "approved-tx-asset-unexpected-field", "asset-unexpected-field",
        "approved-tx-asset-sent-unknown", "asset-sent-unknown",
        "approved-tx-expired", "asset-krw-token-001"
    );

    private final SubjectRefHasher subjectRefHasher;
    private final CanonicalValueHasher hasher;

    public ProjectProvisionalApprovedTransactionAdapter(
        SubjectRefHasher subjectRefHasher,
        CanonicalValueHasher hasher
    ) {
        this.subjectRefHasher = subjectRefHasher;
        this.hasher = hasher;
    }

    @Override
    public Optional<ApprovedTransaction> find(ApprovedTransactionLookup lookup) {
        String assetSymbol = APPROVED_ASSETS.get(lookup.reference().value());
        String subjectDigest = subjectRefHasher.hash(new SubjectRef("customer", "customer-100"));
        if (assetSymbol == null
            || !INSTITUTION_ID.equals(lookup.institutionId())
            || !subjectDigest.equals(lookup.subjectRefDigest())
            || !WORKLOAD_ID.equals(lookup.workloadId())
            || !PURPOSE.equals(lookup.purpose())) {
            return Optional.empty();
        }
        String version = "0.3.0";
        DigitalAssetDescriptor approvedAsset = new DigitalAssetDescriptor(
            "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, assetSymbol,
            "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
        );
        OffsetDateTime approvedFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime approvedUntil = "approved-tx-expired".equals(lookup.reference().value())
            ? OffsetDateTime.parse("2026-01-02T00:00:00Z")
            : OffsetDateTime.parse("2027-01-01T00:00:00Z");
        String identity = String.join("|",
            lookup.reference().value(), version, INSTITUTION_ID, subjectDigest, WORKLOAD_ID, PURPOSE,
            POLICY_SNAPSHOT_ID, approvedAsset.canonicalValue(), "10000000",
            DESTINATION_PROFILE_ID, DESTINATION, BENEFICIARY_REFERENCE,
            approvedFrom.toString(), approvedUntil.toString()
        );
        return Optional.of(new ApprovedTransaction(
            lookup.reference().value(), version, hasher.hash(identity), INSTITUTION_ID, subjectDigest,
            WORKLOAD_ID, PURPOSE, POLICY_SNAPSHOT_ID, approvedAsset, null,
            DigitalAssetAmount.from("10000000"), DESTINATION_PROFILE_ID,
            DESTINATION, BENEFICIARY_REFERENCE, approvedFrom, approvedUntil
        ));
    }
}
