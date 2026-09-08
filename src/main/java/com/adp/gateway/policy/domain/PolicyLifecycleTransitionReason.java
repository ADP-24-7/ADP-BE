package com.adp.gateway.policy.domain;

public enum PolicyLifecycleTransitionReason {
    VALIDATION_PASSED(PolicyLifecycleStage.VALIDATED),
    CANDIDATE_PROMOTED(PolicyLifecycleStage.CANDIDATE),
    REPLAY_PASSED(PolicyLifecycleStage.REPLAY),
    SHADOW_PASSED(PolicyLifecycleStage.SHADOW),
    APPROVAL_GRANTED(PolicyLifecycleStage.APPROVED),
    ACTIVATION_APPROVED(PolicyLifecycleStage.ACTIVE),
    ACTIVE_VERSION_SUPERSEDED(PolicyLifecycleStage.SUPERSEDED),
    REVIEW_OPENED(PolicyLifecycleStage.REVIEW),
    ROLLBACK_APPROVED(PolicyLifecycleStage.ROLLED_BACK);

    private final PolicyLifecycleStage targetStage;

    PolicyLifecycleTransitionReason(PolicyLifecycleStage targetStage) {
        this.targetStage = targetStage;
    }

    public boolean supports(PolicyLifecycleStage target) {
        return targetStage == target;
    }
}
