package com.adp.gateway.decision.domain;

public enum FinalAction {
    ALLOW,
    TRANSFORM,
    REVIEW,
    BLOCK;

    public boolean isAtLeastAsRestrictiveAs(FinalAction baseline) {
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
