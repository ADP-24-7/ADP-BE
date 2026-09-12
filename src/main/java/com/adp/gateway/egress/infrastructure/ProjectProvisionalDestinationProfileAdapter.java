package com.adp.gateway.egress.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
import com.adp.gateway.egress.application.DestinationProfileNotFoundException;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationFieldContract;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.retrieval.domain.DataClass;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalDestinationProfileAdapter implements DestinationProfilePort {

    public static final String PREFLIGHT_POLICY_BLOCK_DESTINATION = "dest_preflight_policy_block";
    public static final String PREFLIGHT_OUTBOUND_BLOCK_DESTINATION = "dest_preflight_outbound_block";

    private final MeterRegistry meterRegistry;
    private final AiModelProfileCatalog aiModelProfiles;

    public ProjectProvisionalDestinationProfileAdapter(
        MeterRegistry meterRegistry,
        AiModelProfileCatalog aiModelProfiles
    ) {
        this.meterRegistry = meterRegistry;
        this.aiModelProfiles = aiModelProfiles;
    }

    @Override
    public DestinationProfile load(String destinationProfileId, OffsetDateTime requestStartedAt) {
        if (PREFLIGHT_POLICY_BLOCK_DESTINATION.equals(destinationProfileId)) {
            meterRegistry.counter("destination.profile.lookup.total", "result", "FOUND").increment();
            return preflightProfile(destinationProfileId, "preflight-policy-block", fieldContracts());
        }
        if (PREFLIGHT_OUTBOUND_BLOCK_DESTINATION.equals(destinationProfileId)) {
            meterRegistry.counter("destination.profile.lookup.total", "result", "FOUND").increment();
            return preflightProfile(
                destinationProfileId,
                "internal-provider",
                fieldContracts().stream()
                    .filter(contract -> !"customer.customer_id".equals(contract.path()))
                    .toList()
            );
        }
        if (DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_ID.equals(destinationProfileId)) {
            meterRegistry.counter("destination.profile.lookup.total", "result", "FOUND").increment();
            return digitalAssetProfile(destinationProfileId);
        }
        var modelProfile = aiModelProfiles.findByDestinationProfileId(destinationProfileId);
        if (modelProfile.isPresent()) {
            meterRegistry.counter("destination.profile.lookup.total", "result", "FOUND").increment();
            return nvidiaProfile(modelProfile.get());
        }
        if (!"dest_internal_provider_project_provisional".equals(destinationProfileId)) {
            meterRegistry.counter("destination.profile.lookup.total", "result", "NOT_FOUND").increment();
            throw new DestinationProfileNotFoundException(destinationProfileId);
        }
        meterRegistry.counter("destination.profile.lookup.total", "result", "FOUND").increment();
        return new DestinationProfile(
            destinationProfileId,
            "0.0.0",
            "local-fixture-destination-profile",
            "be-egress-contract/0.0.0",
            "internal-provider",
            ExecutionPackType.AI,
            "project-provisional-egress-schema-v1",
            "tenant_local_ai",
            "KR",
            "NO_RETENTION",
            false,
            "ACTIVE",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            fieldContracts()
        );
    }

    private DestinationProfile preflightProfile(
        String destinationProfileId,
        String providerProfileId,
        List<DestinationFieldContract> contracts
    ) {
        return new DestinationProfile(
            destinationProfileId,
            "0.0.0",
            "local-fixture-" + destinationProfileId,
            "be-egress-contract/0.0.0",
            providerProfileId,
            ExecutionPackType.AI,
            "project-provisional-egress-schema-v1",
            "tenant_local_ai",
            "KR",
            "NO_RETENTION",
            false,
            "ACTIVE",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            contracts
        );
    }

    private DestinationProfile nvidiaProfile(AiModelProfile modelProfile) {
        return new DestinationProfile(
            modelProfile.destinationProfileId(),
            modelProfile.destinationProfileVersion(),
            modelProfile.destinationProfileDigest(),
            AiModelProfileCatalog.DESTINATION_CONTRACT_VERSION,
            modelProfile.profileId(),
            ExecutionPackType.AI,
            "ai-provider-response/v1",
            "tenant_local_ai_evaluation",
            "NVIDIA_HOSTED",
            "PROVIDER_CONTROLLED",
            false,
            "ACTIVE",
            OffsetDateTime.parse("2026-09-07T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            fieldContracts()
        );
    }

    private DestinationProfile digitalAssetProfile(String destinationProfileId) {
        return new DestinationProfile(
            destinationProfileId, DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_VERSION,
            "local-digital-asset-destination-v1",
            DigitalAssetCanonicalContract.BASELINE_DESTINATION_CONTRACT_VERSION,
            "mock-asset-platform", ExecutionPackType.DIGITAL_ASSET,
            DigitalAssetCanonicalContract.BASELINE_PROVIDER_REQUEST_SCHEMA_VERSION,
            "tenant_local_asset", "KR", "SETTLEMENT_EVIDENCE_ONLY", false,
            "ACTIVE", OffsetDateTime.parse("2026-01-01T00:00:00Z"), null,
            List.of(new DestinationBinding(
                DigitalAssetCanonicalContract.BASELINE_WORKLOAD_ID,
                DigitalAssetCanonicalContract.BASELINE_PURPOSE_CODE
            )),
            List.of(
                new DestinationFieldContract("input.customerId", DataClass.CUSTOMER_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
                new DestinationFieldContract("input.accountId", DataClass.ACCOUNT_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
                new DestinationFieldContract("input.outboundRequest.requestedAsset.chainId", DataClass.BUSINESS_METADATA, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedAsset.assetKind", DataClass.BUSINESS_METADATA, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedAsset.assetSymbol", DataClass.BUSINESS_METADATA, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedAsset.assetContractAddress", DataClass.TRANSACTION_IDENTIFIER, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedAsset.operation", DataClass.BUSINESS_METADATA, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedAmount", DataClass.FINANCIAL_AMOUNT, FieldObligation.REQUIRED_EXACT, true, true),
                new DestinationFieldContract("input.outboundRequest.requestedDestination", DataClass.TRANSACTION_IDENTIFIER, FieldObligation.REQUIRED_EXACT, true, true)
            )
        );
    }

    private List<DestinationFieldContract> fieldContracts() {
        return List.of(
            new DestinationFieldContract("input.prompt", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("customer.customer_id", DataClass.CUSTOMER_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("customer.segment", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("account.account_id", DataClass.ACCOUNT_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("account.account_type", DataClass.FINANCIAL_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("account.balance", DataClass.FINANCIAL_AMOUNT, FieldObligation.REQUIRED_EXACT, true, true),
            new DestinationFieldContract("transaction.transaction_id", DataClass.TRANSACTION_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("transaction.posted_at", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("transaction.merchant_category", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("transaction.amount", DataClass.FINANCIAL_AMOUNT, FieldObligation.REQUIRED_EXACT, true, true)
        );
    }
}
