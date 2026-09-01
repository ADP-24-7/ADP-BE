package com.adp.gateway.egress.application;

import java.util.ArrayList;
import java.util.List;

import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.retrieval.domain.DataClass;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class OutboundGuardChain {

    private final MeterRegistry meterRegistry;

    public OutboundGuardChain(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public OutboundGuardResult guard(
        DestinationProfile destinationProfile,
        String workloadId,
        String purposeCode,
        java.time.OffsetDateTime requestStartedAt,
        RuntimeDecision decision,
        OutboundCandidatePayload payload
    ) {
        Timer.Sample timer = Timer.start(meterRegistry);
        List<String> reasonCodes = new ArrayList<>();
        try {
            if (!destinationProfile.destinationProfileId().equals(payload.destinationProfileId())) {
                reasonCodes.add("DESTINATION_PROFILE_MISMATCH");
            }
            if (!destinationProfile.profileVersion().equals(payload.destinationProfileVersion())
                || !destinationProfile.profileDigest().equals(payload.destinationProfileDigest())) {
                reasonCodes.add("DESTINATION_PROFILE_VERSION_MISMATCH");
            }
            if (destinationProfile.packType() != payload.packType()) {
                reasonCodes.add("PACK_TYPE_MISMATCH");
            }
            if (!destinationProfile.schemaVersion().equals(payload.schemaVersion())) {
                reasonCodes.add("SCHEMA_VERSION_MISMATCH");
            }
            if (!destinationProfile.isEffectiveAt(requestStartedAt)) {
                reasonCodes.add("DESTINATION_PROFILE_NOT_EFFECTIVE");
            }
            if (!destinationProfile.allows(workloadId, purposeCode, requestStartedAt)) {
                reasonCodes.add("DESTINATION_PROFILE_NOT_ALLOWED");
            }
            if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
                reasonCodes.add("FINAL_ACTION_NOT_EGRESSIBLE");
            }
            for (OutboundCandidateField field : payload.fields()) {
                validateField(destinationProfile.fieldContract(field.path()).orElse(null), field, reasonCodes);
            }
            destinationProfile.fieldContracts().stream()
                .filter(contract -> contract.required()
                    && payload.fields().stream().noneMatch(field -> matchesContract(field.path(), contract.path())))
                .forEach(contract -> reasonCodes.add("REQUIRED_FIELD_MISSING"));
            if (!reasonCodes.isEmpty()) {
                recordGuardMetric("REJECTED", reasonCodes);
                return OutboundGuardResult.rejected(reasonCodes);
            }
            recordGuardMetric("PASSED", List.of("NONE"));
            return OutboundGuardResult.passed();
        } finally {
            timer.stop(Timer.builder("egress.guard.duration").register(meterRegistry));
        }
    }

    private void validateField(
        com.adp.gateway.egress.domain.DestinationFieldContract contract,
        OutboundCandidateField field,
        List<String> reasonCodes
    ) {
        if (contract == null) {
            reasonCodes.add("DESTINATION_FIELD_CONTRACT_NOT_FOUND");
        } else if (contract.dataClass() != field.dataClass()) {
            reasonCodes.add("FIELD_DATA_CLASS_MISMATCH");
        }
        if (contract != null && contract.obligation() != field.obligation()) {
            reasonCodes.add("FIELD_OBLIGATION_MISMATCH");
        }
        if (field.dataClass() == DataClass.UNKNOWN || field.obligation() == FieldObligation.PROHIBITED) {
            reasonCodes.add("PROHIBITED_FIELD_PRESENT");
        }
        if (field.treatment() == FieldTreatment.REMOVED) {
            reasonCodes.add("REMOVED_FIELD_PRESENT");
        }
        if (field.treatment() == FieldTreatment.KEEP_EXACT_PROTECTED
            && (contract == null || !contract.exactAllowed())) {
            reasonCodes.add("UNAPPROVED_RAW_FIELD_PRESENT");
        }
        if (field.obligation() == FieldObligation.REQUIRED_EXACT
            && field.treatment() != FieldTreatment.KEEP_EXACT_PROTECTED) {
            reasonCodes.add("REQUIRED_EXACT_NOT_PRESERVED");
        }
        if (field.sensitiveFindings().stream().anyMatch(finding -> "SECRET".equals(finding.findingType()))
            && field.treatment() == FieldTreatment.KEEP_EXACT_PROTECTED) {
            reasonCodes.add("SECRET_FIELD_PRESENT");
        }
        if (field.valueDigest() == null && field.treatment() != FieldTreatment.REMOVED) {
            reasonCodes.add("EGRESS_FIELD_DIGEST_MISSING");
        }
    }

    private boolean matchesContract(String fieldPath, String contractPath) {
        return fieldPath.equals(contractPath) || fieldPath.endsWith("." + contractPath);
    }

    private void recordGuardMetric(String result, List<String> reasonCodes) {
        reasonCodes.forEach(reasonCode -> meterRegistry.counter(
            "egress.guard.total",
            "result", result,
            "reason", reasonCode
        ).increment());
    }
}
