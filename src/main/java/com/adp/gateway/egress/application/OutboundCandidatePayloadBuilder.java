package com.adp.gateway.egress.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.springframework.stereotype.Service;

@Service
public class OutboundCandidatePayloadBuilder {

    private final DestinationProfilePort destinationProfilePort;
    private final CanonicalValueHasher hasher;

    public OutboundCandidatePayloadBuilder(
        DestinationProfilePort destinationProfilePort,
        CanonicalValueHasher hasher
    ) {
        this.destinationProfilePort = destinationProfilePort;
        this.hasher = hasher;
    }

    public OutboundCandidatePayload build(
        RuntimeRequestContext requestContext,
        String providerProfileId,
        RuntimeDecision decision,
        TransformResult transformResult
    ) {
        DestinationProfile profile = destinationProfilePort.load(providerProfileId);
        List<OutboundCandidateField> fields = transformResult.fields().stream()
            .filter(field -> field.strategy() != TransformStrategy.REMOVE)
            .map(this::field)
            .sorted(Comparator.comparing(OutboundCandidateField::path))
            .toList();
        String payloadDigest = hasher.hash(String.join("|",
            profile.destinationProfileId(),
            profile.schemaVersion(),
            decision.decisionId(),
            transformResult.outputDigest() == null ? "<none>" : transformResult.outputDigest(),
            fields.stream()
                .map(field -> field.path() + ":" + field.dataClass().name() + ":" + field.treatment().name() + ":"
                    + (field.valueDigest() == null ? "<none>" : field.valueDigest()))
                .collect(Collectors.joining("|"))
        ));
        return new OutboundCandidatePayload(
            "out_" + UUID.randomUUID(),
            profile.destinationProfileId(),
            profile.packType(),
            profile.schemaVersion(),
            payloadDigest,
            fields
        );
    }

    private OutboundCandidateField field(TransformFieldResult field) {
        return new OutboundCandidateField(
            field.path(),
            field.dataClass(),
            field.strategy(),
            obligation(field.dataClass(), field.strategy()),
            treatment(field.strategy()),
            field.transformedValueDigest(),
            field.transformedValue()
        );
    }

    private FieldObligation obligation(DataClass dataClass, TransformStrategy strategy) {
        if (strategy == TransformStrategy.KEEP) {
            return switch (dataClass) {
                case BUSINESS_METADATA, FINANCIAL_METADATA -> FieldObligation.CONDITIONAL_EXACT;
                default -> FieldObligation.REQUIRED_EXACT;
            };
        }
        return switch (dataClass) {
            case UNKNOWN -> FieldObligation.PROHIBITED;
            case BUSINESS_METADATA, FINANCIAL_METADATA -> FieldObligation.MINIMIZABLE;
            default -> FieldObligation.PSEUDONYMIZABLE;
        };
    }

    private FieldTreatment treatment(TransformStrategy strategy) {
        return switch (strategy) {
            case REMOVE -> FieldTreatment.REMOVED;
            case KEEP -> FieldTreatment.KEEP_EXACT_PROTECTED;
            case MASK, HMAC_PSEUDO, VAULT_TOKEN, GENERALIZE, FIELD_SEPARATION -> FieldTreatment.TRANSFORMED;
        };
    }
}
