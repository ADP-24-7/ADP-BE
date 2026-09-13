package com.adp.gateway.digitalasset.application;

public class InvalidDigitalAssetOverviewRangeException extends RuntimeException {
    public InvalidDigitalAssetOverviewRangeException() {
        super("Digital Asset overview range must be positive and at most 31 days");
    }
}
