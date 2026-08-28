package com.adp.gateway.decision.domain;

import com.adp.gateway.policy.domain.PolicyAction;

public enum FinalAction {
    ALLOW,
    TRANSFORM,
    REVIEW,
    BLOCK;

    public boolean isAtLeastAsRestrictiveAs(PolicyAction baseline) {
        return rank() >= rank(baseline);
    }

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

    private int rank(PolicyAction action) {
        return switch (action) {
            case ALLOW -> 0;
            case TRANSFORM -> 1;
            case REVIEW -> 2;
            case BLOCK -> 3;
        };
    }
}
