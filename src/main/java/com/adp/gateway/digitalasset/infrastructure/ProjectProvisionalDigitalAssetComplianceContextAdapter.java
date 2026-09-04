package com.adp.gateway.digitalasset.infrastructure;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.application.DigitalAssetComplianceContextPort;
import com.adp.gateway.digitalasset.domain.DigitalAssetComplianceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "'${adp.local-fixtures.enabled:false}' == 'true' && "
        + "'${adp.digital-asset.compliance-fixture.enabled:true}' == 'true'"
)
public class ProjectProvisionalDigitalAssetComplianceContextAdapter
    implements DigitalAssetComplianceContextPort {

    public static final String SOURCE_SYSTEM = "BANK_COMPLIANCE_FIXTURE";
    public static final String ASSERTION_VERSION = "1.0.0";

    private final CanonicalValueHasher hasher;

    public ProjectProvisionalDigitalAssetComplianceContextAdapter(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public DigitalAssetComplianceContext load(String customerId, String accountId, String walletAddress) {
        String kycStatus = "wallet-kyc-pending".equals(walletAddress) ? "PENDING" : "VERIFIED";
        String amlStatus = "wallet-aml-review".equals(walletAddress) ? "REVIEW" : "PASSED";
        boolean walletVerified = !"wallet-unverified".equals(walletAddress);
        String digest = hasher.hash(String.join("|",
            SOURCE_SYSTEM, ASSERTION_VERSION, customerId, accountId, walletAddress,
            kycStatus, amlStatus, Boolean.toString(walletVerified)
        ));
        return new DigitalAssetComplianceContext(
            kycStatus, amlStatus, walletVerified, SOURCE_SYSTEM, ASSERTION_VERSION, digest
        );
    }
}
