package com.adp.gateway.digitalasset.infrastructure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.application.DigitalAssetPolicyProfilePort;
import com.adp.gateway.digitalasset.domain.DigitalAssetPolicyProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalDigitalAssetPolicyProfileAdapter implements DigitalAssetPolicyProfilePort {

    public static final String PROFILE_ID = "digital-asset-policy/local/1";
    public static final String PROFILE_VERSION = "1.0.0";
    public static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000");
    public static final String DESTINATION_PROFILE_ID = "dest_mock_asset_platform_v1";
    public static final String COMPLIANCE_SOURCE_SYSTEM = "BANK_COMPLIANCE_FIXTURE";
    public static final String COMPLIANCE_ASSERTION_VERSION = "1.0.0";

    private final CanonicalValueHasher hasher;

    public ProjectProvisionalDigitalAssetPolicyProfileAdapter(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public DigitalAssetPolicyProfile load(String destinationProfileId, OffsetDateTime requestStartedAt) {
        if (!DESTINATION_PROFILE_ID.equals(destinationProfileId)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_POLICY_PROFILE_NOT_FOUND");
        }
        String digest = hasher.hash(String.join("|",
            PROFILE_ID, PROFILE_VERSION, destinationProfileId,
            COMPLIANCE_SOURCE_SYSTEM, COMPLIANCE_ASSERTION_VERSION,
            "VERIFIED", "PASSED", "true",
            AMOUNT_LIMIT.toPlainString(), "2026-01-01T00:00:00Z"
        ));
        return new DigitalAssetPolicyProfile(
            PROFILE_ID,
            PROFILE_VERSION,
            digest,
            destinationProfileId,
            COMPLIANCE_SOURCE_SYSTEM,
            COMPLIANCE_ASSERTION_VERSION,
            Set.of("VERIFIED"),
            Set.of("PASSED"),
            true,
            AMOUNT_LIMIT,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null
        );
    }
}
