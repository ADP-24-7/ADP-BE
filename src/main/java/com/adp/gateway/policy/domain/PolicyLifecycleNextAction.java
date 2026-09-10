package com.adp.gateway.policy.domain;

public enum PolicyLifecycleNextAction {
    VALIDATE,
    PROMOTE_CANDIDATE,
    START_REPLAY,
    RUN_SHADOW,
    APPROVE,
    ACTIVATE,
    ROLLBACK
}
