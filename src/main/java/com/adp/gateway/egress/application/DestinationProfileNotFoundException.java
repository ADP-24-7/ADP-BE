package com.adp.gateway.egress.application;

public class DestinationProfileNotFoundException extends RuntimeException {

    public DestinationProfileNotFoundException(String providerProfileId) {
        super("Destination profile is not configured: " + providerProfileId);
    }
}
