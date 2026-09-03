package com.adp.gateway.context.application;

import com.adp.gateway.egress.domain.ExecutionPackType;

public class ExecutionPackInputRejectedException extends RuntimeException {

    private final ExecutionPackType packType;
    private final String reasonCode;

    public ExecutionPackInputRejectedException(ExecutionPackType packType, String reasonCode) {
        super("Execution pack input did not satisfy the workload contract");
        this.packType = packType;
        this.reasonCode = reasonCode;
    }

    public ExecutionPackType packType() {
        return packType;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
