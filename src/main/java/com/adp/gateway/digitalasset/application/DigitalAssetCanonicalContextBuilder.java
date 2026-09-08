package com.adp.gateway.digitalasset.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.application.ExecutionPackContextBuilder;
import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.context.application.ExecutionPackRequestScope;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeInput;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetCanonicalContextBuilder implements ExecutionPackContextBuilder {

    public static final String WORKLOAD_ID = "tokenized_asset_purchase";
    public static final String PURPOSE = "DIGITAL_ASSET_PURCHASE";
    private final CanonicalValueHasher hasher;
    private final SubjectRefHasher subjectRefHasher;
    private final ApprovedTransactionResolver approvedTransactionResolver;

    public DigitalAssetCanonicalContextBuilder(
        CanonicalValueHasher hasher,
        SubjectRefHasher subjectRefHasher,
        ApprovedTransactionResolver approvedTransactionResolver
    ) {
        this.hasher = hasher;
        this.subjectRefHasher = subjectRefHasher;
        this.approvedTransactionResolver = approvedTransactionResolver;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public CanonicalContext merge(
        CanonicalContext retrievalContext,
        Map<String, Object> input,
        ExecutionPackRequestScope requestScope
    ) {
        validate(input);
        DigitalAssetRuntimeInput runtimeInput = parse(input, requestScope);
        String inputSubjectDigest = subjectRefHasher.hash(new SubjectRef("customer", runtimeInput.customerId()));
        if (!inputSubjectDigest.equals(retrievalContext.subjectRefDigest())) {
            throw new ExecutionPackInputRejectedException(
                ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_SUBJECT_MISMATCH"
            );
        }
        if (!inputSubjectDigest.equals(requestScope.subjectRefDigest())
            || !retrievalContext.workloadId().equals(requestScope.workloadId())
            || !retrievalContext.purpose().equals(requestScope.purpose())) {
            throw new ExecutionPackInputRejectedException(
                ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_RUNTIME_SCOPE_MISMATCH"
            );
        }
        var approved = approvedTransactionResolver.resolve(new ApprovedTransactionLookup(
            runtimeInput.approvedTransactionReference(), requestScope.institutionId(), inputSubjectDigest,
            requestScope.workloadId(), requestScope.purpose()
        ));
        var outbound = runtimeInput.outboundRequest();
        List<CanonicalContextField> fields = new ArrayList<>(retrievalContext.fields());
        add(fields, "customerId", runtimeInput.customerId(), DataClass.CUSTOMER_IDENTIFIER);
        add(fields, "accountId", runtimeInput.accountId(), DataClass.ACCOUNT_IDENTIFIER);
        add(fields, "outboundRequest.requestedAsset.chainId", outbound.requestedAsset().chainId(), DataClass.BUSINESS_METADATA);
        add(fields, "outboundRequest.requestedAsset.assetKind", outbound.requestedAsset().assetKind().name(), DataClass.BUSINESS_METADATA);
        add(fields, "outboundRequest.requestedAsset.assetSymbol", outbound.requestedAsset().assetSymbol(), DataClass.BUSINESS_METADATA);
        addOptional(fields, "outboundRequest.requestedAsset.assetContractAddress",
            outbound.requestedAsset().assetContractAddress(), DataClass.TRANSACTION_IDENTIFIER);
        add(fields, "outboundRequest.requestedAsset.operation", outbound.requestedAsset().operation().name(), DataClass.BUSINESS_METADATA);
        addOptional(fields, "outboundRequest.requestedAsset.tokenId",
            outbound.requestedAsset().tokenId(), DataClass.TRANSACTION_IDENTIFIER);
        add(fields, "outboundRequest.requestedAmount", outbound.requestedAmount().toString(), DataClass.FINANCIAL_AMOUNT);
        add(fields, "outboundRequest.requestedDestination", outbound.requestedDestination(), DataClass.TRANSACTION_IDENTIFIER);
        add(fields, "outboundRequest.requestedBeneficiaryReference",
            outbound.requestedBeneficiaryReference(), DataClass.BUSINESS_METADATA);
        outbound.regulatoryOutboundData().forEach((key, value) ->
            add(fields, "outboundRequest.regulatoryOutboundData." + key, value, DataClass.FINANCIAL_METADATA)
        );
        fields.sort(Comparator.comparing(CanonicalContextField::path));
        var trustedMetadata = new java.util.HashMap<String, String>();
        trustedMetadata.put("approvedTransactionId", approved.approvedTransactionId());
        trustedMetadata.put("approvedTransactionVersion", approved.version());
        trustedMetadata.put("approvedTransactionDigest", approved.digest());
        trustedMetadata.put("approvedPolicySnapshotId", approved.approvedPolicySnapshotId());
        trustedMetadata.put("approvedAsset", approved.approvedAsset().canonicalValue());
        if (approved.approvedAmount() != null) {
            trustedMetadata.put("approvedAmount", approved.approvedAmount().toString());
        }
        if (approved.approvedAmountLimit() != null) {
            trustedMetadata.put("approvedAmountLimit", approved.approvedAmountLimit().toString());
        }
        trustedMetadata.put("approvedDestinationProfileId", approved.approvedDestinationProfileId());
        trustedMetadata.put("approvedDestination", approved.approvedDestination());
        trustedMetadata.put("approvedBeneficiaryReference", approved.approvedBeneficiaryReference());
        trustedMetadata.put("approvedFrom", approved.approvedFrom().toString());
        trustedMetadata.put("approvedUntil", approved.approvedUntil().toString());
        String digest = hasher.hash(
            fields.stream()
                .map(field -> field.path() + ":" + field.dataClass() + ":" + field.valueDigest())
                .collect(Collectors.joining("|"))
                + "|approved-transaction:" + approved.digest()
        );
        return new CanonicalContext(
            retrievalContext.schemaVersion(), retrievalContext.contextId(), retrievalContext.dataAccessId(),
            retrievalContext.workloadId(), retrievalContext.purpose(), retrievalContext.subjectType(),
            retrievalContext.subjectRefDigest(), List.copyOf(fields), Map.copyOf(trustedMetadata), digest
        );
    }

    @Override
    public void validate(Map<String, Object> input) {
        try {
            DigitalAssetRuntimeInput.validateShape(input);
        } catch (IllegalArgumentException exception) {
            throw new ExecutionPackInputRejectedException(ExecutionPackType.DIGITAL_ASSET, exception.getMessage());
        }
    }

    private DigitalAssetRuntimeInput parse(Map<String, Object> input, ExecutionPackRequestScope requestScope) {
        return DigitalAssetRuntimeInput.from(input, requestScope);
    }

    private void add(List<CanonicalContextField> fields, String name, Object value, DataClass dataClass) {
        String path = "$.input." + name;
        fields.add(new CanonicalContextField(path, "request", name, dataClass, value,
            hasher.hash(path + ":" + dataClass + ":" + value)));
    }

    private void addOptional(
        List<CanonicalContextField> fields,
        String name,
        Object value,
        DataClass dataClass
    ) {
        if (value != null) {
            add(fields, name, value, dataClass);
        }
    }
}
