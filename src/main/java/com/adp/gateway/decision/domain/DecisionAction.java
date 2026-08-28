package com.adp.gateway.decision.domain;

public enum DecisionAction {
    ALLOW,
    TRANSFORM,
    REVIEW,
    BLOCK;

    public boolean isAtLeastAsRestrictiveAs(DecisionAction baseline) {
        return rank() >= baseline.rank();
    }

    private int rank() {
        return switch (this) {
            case ALLOW -> 0;
            case TRANSFORM -> 1;
            case REVIEW -> 2;
            case BLOCK -> 3;
        };
    }
}
