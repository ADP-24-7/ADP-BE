package com.adp.gateway.egress.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

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

    private final MeterRegistry meterRegistry;

    public ProjectProvisionalDestinationProfileAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DestinationProfile load(String destinationProfileId, OffsetDateTime requestStartedAt) {
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

    private List<DestinationFieldContract> fieldContracts() {
        return List.of(
            new DestinationFieldContract("input.prompt", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("customer.customer_id", DataClass.CUSTOMER_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("customer.segment", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("account.account_id", DataClass.ACCOUNT_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("account.account_type", DataClass.FINANCIAL_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("account.balance", DataClass.FINANCIAL_AMOUNT, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("transaction.transaction_id", DataClass.TRANSACTION_IDENTIFIER, FieldObligation.PSEUDONYMIZABLE, true, false),
            new DestinationFieldContract("transaction.posted_at", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("transaction.merchant_category", DataClass.BUSINESS_METADATA, FieldObligation.CONDITIONAL_EXACT, true, true),
            new DestinationFieldContract("transaction.amount", DataClass.FINANCIAL_AMOUNT, FieldObligation.PSEUDONYMIZABLE, true, false)
        );
    }
}
