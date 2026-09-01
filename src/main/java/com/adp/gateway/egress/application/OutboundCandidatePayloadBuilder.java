package com.adp.gateway.egress.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationFieldContract;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import com.adp.gateway.transform.domain.TransformStrategy;
import org.springframework.stereotype.Service;

@Service
public class OutboundCandidatePayloadBuilder {

    private final CanonicalValueHasher hasher;
    private final OutboundSensitiveFindingDetector sensitiveFindingDetector;

    public OutboundCandidatePayloadBuilder(
        CanonicalValueHasher hasher,
        OutboundSensitiveFindingDetector sensitiveFindingDetector
    ) {
        this.hasher = hasher;
        this.sensitiveFindingDetector = sensitiveFindingDetector;
    }

    public OutboundCandidatePayload build(
        DestinationProfile profile,
        CanonicalContext canonicalContext,
        RuntimeDecision decision,
        TransformResult transformResult
    ) {
        List<OutboundCandidateField> fields = fields(profile, canonicalContext, decision, transformResult).stream()
            .sorted(Comparator.comparing(OutboundCandidateField::path))
            .toList();
        String candidatePayloadDigest = hasher.hash(String.join("|",
            profile.destinationProfileId(),
            profile.profileVersion(),
            profile.profileDigest(),
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
            profile.profileVersion(),
            profile.profileDigest(),
            profile.packType(),
            profile.schemaVersion(),
            candidatePayloadDigest,
            fields
        );
    }

    private List<OutboundCandidateField> fields(
        DestinationProfile profile,
        CanonicalContext canonicalContext,
        RuntimeDecision decision,
        TransformResult transformResult
    ) {
        if (decision.finalAction() == FinalAction.ALLOW) {
            return canonicalContext.fields().stream()
                .map(field -> exactField(profile, field))
                .toList();
        }
        return transformResult.fields().stream()
            .filter(field -> field.strategy() != TransformStrategy.REMOVE)
            .map(field -> transformedField(profile, field))
            .toList();
    }

    private OutboundCandidateField exactField(DestinationProfile profile, CanonicalContextField field) {
        DestinationFieldContract contract = contract(profile, field.path(), field.dataClass());
        OutboundCandidateField candidateField = new OutboundCandidateField(
            field.path(),
            field.dataClass(),
            TransformStrategy.KEEP,
            contract.obligation(),
            FieldTreatment.KEEP_EXACT_PROTECTED,
            field.valueDigest(),
            List.of(),
            field.value()
        );
        return withFindings(candidateField);
    }

    private OutboundCandidateField transformedField(DestinationProfile profile, TransformFieldResult field) {
        DestinationFieldContract contract = contract(profile, field.path(), field.dataClass());
        OutboundCandidateField candidateField = new OutboundCandidateField(
            field.path(),
            field.dataClass(),
            field.strategy(),
            contract.obligation(),
            treatment(field.strategy()),
            field.transformedValueDigest(),
            List.of(),
            field.transformedValue()
        );
        return withFindings(candidateField);
    }

    private OutboundCandidateField withFindings(OutboundCandidateField field) {
        return new OutboundCandidateField(
            field.path(),
            field.dataClass(),
            field.strategy(),
            field.obligation(),
            field.treatment(),
            field.valueDigest(),
            sensitiveFindingDetector.detect(field),
            field.value()
        );
    }

    private DestinationFieldContract contract(DestinationProfile profile, String path, com.adp.gateway.retrieval.domain.DataClass dataClass) {
        return profile.fieldContract(path)
            .orElse(new DestinationFieldContract(path, dataClass, FieldObligation.PROHIBITED, false, false));
    }

    private FieldTreatment treatment(TransformStrategy strategy) {
        return switch (strategy) {
            case REMOVE -> FieldTreatment.REMOVED;
            case KEEP -> FieldTreatment.KEEP_EXACT_PROTECTED;
            case MASK, HMAC_PSEUDO, VAULT_TOKEN, GENERALIZE, FIELD_SEPARATION -> FieldTreatment.TRANSFORMED;
        };
    }
}
