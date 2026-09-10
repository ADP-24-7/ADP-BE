package com.adp.gateway.operations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.adp.gateway.operations.domain.ReviewNextAction;
import com.adp.gateway.operations.domain.ReviewSource;
import org.junit.jupiter.api.Test;

class ReviewQueueClassifierTests {
    @Test
    void preservesEveryReviewSourceActionAndReasonWithDeterministicPriority() {
        var classification = ReviewQueueClassifier.classify(
            "MANUAL_REVIEW",
            "REVIEW_REQUIRED",
            "SENSITIVE_INPUT_REVIEW_REQUIRED,POLICY_INCOMPLETE",
            "POLICY_INCOMPLETE",
            "EXTERNAL_STATUS_AMBIGUOUS"
        );

        assertThat(classification.primarySource()).isEqualTo(ReviewSource.RECOVERY);
        assertThat(classification.sources()).containsExactly(
            ReviewSource.RECOVERY, ReviewSource.POST_EXECUTION, ReviewSource.POLICY
        );
        assertThat(classification.primaryAction()).isEqualTo(ReviewNextAction.RECONCILE_EXTERNAL_STATUS);
        assertThat(classification.actions()).containsExactly(
            ReviewNextAction.RECONCILE_EXTERNAL_STATUS,
            ReviewNextAction.INSPECT_POST_EXECUTION_EVIDENCE,
            ReviewNextAction.INSPECT_TRACE
        );
        assertThat(classification.reasonCodes()).containsExactly(
            "SENSITIVE_INPUT_REVIEW_REQUIRED", "POLICY_INCOMPLETE", "EXTERNAL_STATUS_AMBIGUOUS"
        );
    }

    @Test
    void fallsBackToPolicyReviewWhenNoSpecializedEvidenceExists() {
        var classification = ReviewQueueClassifier.classify(null, null, null, null, null);

        assertThat(classification.sources()).isEqualTo(List.of(ReviewSource.POLICY));
        assertThat(classification.actions()).isEqualTo(List.of(ReviewNextAction.INSPECT_TRACE));
        assertThat(classification.reasonCodes()).isEmpty();
    }
}
