package com.adp.gateway.egress.application;

import java.util.ArrayList;
import java.util.List;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Service;

@Service
public class OutboundGuardChain {

    private final DestinationProfilePort destinationProfilePort;

    public OutboundGuardChain(DestinationProfilePort destinationProfilePort) {
        this.destinationProfilePort = destinationProfilePort;
    }

    public OutboundGuardResult guard(
        RuntimeRequestContext requestContext,
        String providerProfileId,
        RuntimeDecision decision,
        OutboundCandidatePayload payload
    ) {
        List<String> reasonCodes = new ArrayList<>();
        var destinationProfile = destinationProfilePort.load(providerProfileId);
        if (!destinationProfile.destinationProfileId().equals(payload.destinationProfileId())) {
            reasonCodes.add("DESTINATION_PROFILE_MISMATCH");
        }
        if (!destinationProfile.allows(requestContext.workloadId(), requestContext.purpose())) {
            reasonCodes.add("DESTINATION_PROFILE_NOT_ALLOWED");
        }
        if (decision.finalAction() != FinalAction.ALLOW && decision.finalAction() != FinalAction.TRANSFORM) {
            reasonCodes.add("FINAL_ACTION_NOT_EGRESSIBLE");
        }
        for (OutboundCandidateField field : payload.fields()) {
            validateField(field, reasonCodes);
        }
        if (!reasonCodes.isEmpty()) {
            throw new OutboundGuardException("Outbound guard rejected payload", reasonCodes);
        }
        return OutboundGuardResult.passed();
    }

    private void validateField(OutboundCandidateField field, List<String> reasonCodes) {
        if (field.dataClass() == DataClass.UNKNOWN || field.obligation() == FieldObligation.PROHIBITED) {
            reasonCodes.add("PROHIBITED_FIELD_PRESENT");
        }
        if (field.treatment() == FieldTreatment.REMOVED) {
            reasonCodes.add("REMOVED_FIELD_PRESENT");
        }
        if (field.treatment() == FieldTreatment.KEEP_EXACT_PROTECTED
            && field.dataClass() != DataClass.BUSINESS_METADATA
            && field.dataClass() != DataClass.FINANCIAL_METADATA) {
            reasonCodes.add("UNAPPROVED_RAW_FIELD_PRESENT");
        }
        if (field.valueDigest() == null && field.treatment() != FieldTreatment.REMOVED) {
            reasonCodes.add("EGRESS_FIELD_DIGEST_MISSING");
        }
    }
}
