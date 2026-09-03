package com.adp.gateway.egress.application;

import com.adp.gateway.egress.domain.ExecutionPackType;

public class PackRuntimeAdapterNotFoundException extends RuntimeException {

    public PackRuntimeAdapterNotFoundException(String adapterType, ExecutionPackType packType) {
        super("No " + adapterType + " configured for execution pack " + packType);
    }
}
