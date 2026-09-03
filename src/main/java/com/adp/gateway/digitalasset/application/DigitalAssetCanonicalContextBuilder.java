package com.adp.gateway.digitalasset.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackContextBuilder;
import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetPurchaseInput;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetCanonicalContextBuilder implements ExecutionPackContextBuilder {

    public static final String WORKLOAD_ID = "tokenized_asset_purchase";
    public static final String PURPOSE = "DIGITAL_ASSET_PURCHASE";
    private final CanonicalValueHasher hasher;
    private final SubjectRefHasher subjectRefHasher;

    public DigitalAssetCanonicalContextBuilder(CanonicalValueHasher hasher, SubjectRefHasher subjectRefHasher) {
        this.hasher = hasher;
        this.subjectRefHasher = subjectRefHasher;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public CanonicalContext merge(CanonicalContext retrievalContext, Map<String, Object> input) {
        validate(input);
        DigitalAssetPurchaseInput purchase = parse(input);
        String inputSubjectDigest = subjectRefHasher.hash(new SubjectRef("customer", purchase.customerId()));
        if (!inputSubjectDigest.equals(retrievalContext.subjectRefDigest())) {
            throw new ExecutionPackInputRejectedException(
                ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_SUBJECT_MISMATCH"
            );
        }
        List<CanonicalContextField> fields = new ArrayList<>(retrievalContext.fields());
        add(fields, "customerId", purchase.customerId(), DataClass.CUSTOMER_IDENTIFIER);
        add(fields, "accountId", purchase.accountId(), DataClass.ACCOUNT_IDENTIFIER);
        add(fields, "walletAddress", purchase.walletAddress(), DataClass.TRANSACTION_IDENTIFIER);
        add(fields, "assetId", purchase.assetId(), DataClass.BUSINESS_METADATA);
        add(fields, "amount", purchase.amount().toPlainString(), DataClass.FINANCIAL_AMOUNT);
        add(fields, "kycStatus", purchase.kycStatus(), DataClass.FINANCIAL_METADATA);
        add(fields, "amlStatus", purchase.amlStatus(), DataClass.FINANCIAL_METADATA);
        add(fields, "walletVerified", purchase.walletVerified(), DataClass.FINANCIAL_METADATA);
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
        try {
            DigitalAssetPurchaseInput.from(input);
        } catch (IllegalArgumentException exception) {
            throw new ExecutionPackInputRejectedException(ExecutionPackType.DIGITAL_ASSET, exception.getMessage());
        }
    }

    private DigitalAssetPurchaseInput parse(Map<String, Object> input) {
        return DigitalAssetPurchaseInput.from(input);
    }

    private void add(List<CanonicalContextField> fields, String name, Object value, DataClass dataClass) {
        String path = "$.input." + name;
        fields.add(new CanonicalContextField(path, "request", name, dataClass, value,
            hasher.hash(path + ":" + dataClass + ":" + value)));
    }
}
