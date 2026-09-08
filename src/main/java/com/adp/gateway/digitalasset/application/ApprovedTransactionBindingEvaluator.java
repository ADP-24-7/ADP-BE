package com.adp.gateway.digitalasset.application;

import java.util.ArrayList;
import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.digitalasset.domain.ApprovedTransaction;
import com.adp.gateway.digitalasset.domain.OutboundRequest;
import org.springframework.stereotype.Component;

@Component
public class ApprovedTransactionBindingEvaluator {

    public List<ReasonCode> evaluate(ApprovedTransaction approved, OutboundRequest request) {
        List<ReasonCode> reasons = new ArrayList<>();
        if (!approved.approvedDestinationProfileId().equals(request.destinationProfileId())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_PROFILE_MISMATCH);
        }
        if (!approved.approvedAsset().equals(request.requestedAsset())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_ASSET_MISMATCH);
        }
        if ((approved.approvedAmount() != null
                && !approved.approvedAmount().equals(request.requestedAmount()))
            || (approved.approvedAmount() == null
                && (approved.approvedAmountLimit() == null
                    || request.requestedAmount().compareTo(approved.approvedAmountLimit()) > 0))) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED);
        }
        if (!approved.approvedDestination().equals(request.requestedDestination())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH);
        }
        if (!approved.approvedBeneficiaryReference().equals(request.requestedBeneficiaryReference())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_BENEFICIARY_MISMATCH);
        }
        if (request.requestedAt().isBefore(approved.approvedFrom())
            || request.requestedAt().isAfter(approved.approvedUntil())) {
            reasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_PERIOD_VIOLATION);
        }
        return List.copyOf(reasons);
    }
}
