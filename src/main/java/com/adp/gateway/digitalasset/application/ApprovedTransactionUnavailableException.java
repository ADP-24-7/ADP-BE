package com.adp.gateway.digitalasset.application;

import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.egress.domain.ExecutionPackType;

public class ApprovedTransactionUnavailableException extends ExecutionPackInputRejectedException {
    public ApprovedTransactionUnavailableException() {
        super(ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_APPROVED_TRANSACTION_NOT_FOUND");
    }
}
