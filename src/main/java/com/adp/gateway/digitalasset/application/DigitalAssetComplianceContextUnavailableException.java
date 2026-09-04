package com.adp.gateway.digitalasset.application;

import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.egress.domain.ExecutionPackType;

public class DigitalAssetComplianceContextUnavailableException extends ExecutionPackInputRejectedException {

    public DigitalAssetComplianceContextUnavailableException() {
        super(ExecutionPackType.DIGITAL_ASSET, "DIGITAL_ASSET_COMPLIANCE_CONTEXT_NOT_CONFIGURED");
    }
}
