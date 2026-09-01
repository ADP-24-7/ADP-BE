package com.adp.gateway.egress.application;

public class DestinationProfileNotFoundException extends RuntimeException {

    public DestinationProfileNotFoundException(String destinationProfileId) {
        super("Destination profile is not configured: " + destinationProfileId);
    }
}
