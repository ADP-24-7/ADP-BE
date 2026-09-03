package com.adp.gateway.digitalasset.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackContextBuilder;
import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetCanonicalContextBuilder implements ExecutionPackContextBuilder {

    public static final String WORKLOAD_ID = "tokenized_asset_purchase";
    public static final String PURPOSE = "DIGITAL_ASSET_PURCHASE";
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "customerId", "accountId", "walletAddress", "assetId", "amount",
        "kycStatus", "amlStatus", "walletVerified"
    );

    private final CanonicalValueHasher hasher;

    public DigitalAssetCanonicalContextBuilder(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public CanonicalContext merge(CanonicalContext retrievalContext, Map<String, Object> input) {
        validate(input);
        List<CanonicalContextField> fields = new ArrayList<>(retrievalContext.fields());
        add(fields, "customerId", input.get("customerId"), DataClass.CUSTOMER_IDENTIFIER);
        add(fields, "accountId", input.get("accountId"), DataClass.ACCOUNT_IDENTIFIER);
        add(fields, "walletAddress", input.get("walletAddress"), DataClass.TRANSACTION_IDENTIFIER);
        add(fields, "assetId", input.get("assetId"), DataClass.BUSINESS_METADATA);
        add(fields, "amount", input.get("amount"), DataClass.FINANCIAL_AMOUNT);
        add(fields, "kycStatus", input.get("kycStatus"), DataClass.FINANCIAL_METADATA);
        add(fields, "amlStatus", input.get("amlStatus"), DataClass.FINANCIAL_METADATA);
        add(fields, "walletVerified", input.get("walletVerified"), DataClass.FINANCIAL_METADATA);
        fields.sort(Comparator.comparing(CanonicalContextField::path));
        String digest = hasher.hash(fields.stream()
            .map(field -> field.path() + ":" + field.dataClass() + ":" + field.valueDigest())
            .collect(Collectors.joining("|")));
        return new CanonicalContext(
            retrievalContext.schemaVersion(), retrievalContext.contextId(), retrievalContext.dataAccessId(),
            retrievalContext.workloadId(), retrievalContext.purpose(), retrievalContext.subjectType(),
            retrievalContext.subjectRefDigest(), List.copyOf(fields), digest
        );
    }

    @Override
    public void validate(Map<String, Object> input) {
        if (input == null || !input.keySet().equals(REQUIRED_KEYS)) {
            throw new ExecutionPackInputRejectedException(ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_INPUT_SCHEMA_MISMATCH");
        }
        if (!text(input.get("customerId")) || !text(input.get("accountId"))
            || !text(input.get("walletAddress")) || !text(input.get("assetId"))
            || !text(input.get("kycStatus")) || !text(input.get("amlStatus"))
            || !(input.get("walletVerified") instanceof Boolean)
            || !(input.get("amount") instanceof Number amount) || new BigDecimal(amount.toString()).signum() <= 0) {
            throw new ExecutionPackInputRejectedException(ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_INPUT_INVALID");
        }
    }

    private boolean text(Object value) {
        return value instanceof String text && !text.isBlank() && text.length() <= 240;
    }

    private void add(List<CanonicalContextField> fields, String name, Object value, DataClass dataClass) {
        String path = "$.input." + name;
        fields.add(new CanonicalContextField(path, "request", name, dataClass, value,
            hasher.hash(path + ":" + dataClass + ":" + value)));
    }
}
