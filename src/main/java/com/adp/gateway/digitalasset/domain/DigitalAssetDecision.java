package com.adp.gateway.digitalasset.domain;

import com.adp.gateway.decision.domain.FinalAction;

public enum DigitalAssetDecision {
    PASS,
    BLOCK,
    REVIEW;

    public static DigitalAssetDecision from(FinalAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Digital Asset final action is required");
        }
        return switch (action) {
            case ALLOW, TRANSFORM -> PASS;
            case BLOCK -> BLOCK;
            case REVIEW -> REVIEW;
        };
    }
}
