package com.adp.gateway.operations.infrastructure;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import com.adp.gateway.operations.domain.ReviewNextAction;
import com.adp.gateway.operations.domain.ReviewSource;

final class ReviewQueueClassifier {
    private ReviewQueueClassifier() {
    }

    static Classification classify(
        String recoveryStatus,
        String postExecutionStatus,
        String policyReasons,
        String decisionReasons,
        String recoveryError
    ) {
        LinkedHashSet<ReviewSource> sources = new LinkedHashSet<>();
        if ("MANUAL_REVIEW".equals(recoveryStatus) || "EXHAUSTED".equals(recoveryStatus)) {
            sources.add(ReviewSource.RECOVERY);
        }
        if ("REVIEW_REQUIRED".equals(postExecutionStatus)) sources.add(ReviewSource.POST_EXECUTION);
        if (hasText(policyReasons) || hasText(decisionReasons) || sources.isEmpty()) {
            sources.add(ReviewSource.POLICY);
        }
        List<ReviewSource> sourceList = List.copyOf(sources);
        List<ReviewNextAction> actions = sourceList.stream()
            .map(ReviewQueueClassifier::nextAction)
            .distinct()
            .toList();
        List<String> reasons = Arrays.stream(new String[] { policyReasons, decisionReasons, recoveryError })
            .filter(ReviewQueueClassifier::hasText)
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .filter(ReviewQueueClassifier::hasText)
            .distinct()
            .toList();
        return new Classification(sourceList.getFirst(), sourceList, actions.getFirst(), actions, reasons);
    }

    private static ReviewNextAction nextAction(ReviewSource source) {
        return switch (source) {
            case RECOVERY -> ReviewNextAction.RECONCILE_EXTERNAL_STATUS;
            case POST_EXECUTION -> ReviewNextAction.INSPECT_POST_EXECUTION_EVIDENCE;
            case POLICY -> ReviewNextAction.INSPECT_TRACE;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record Classification(
        ReviewSource primarySource,
        List<ReviewSource> sources,
        ReviewNextAction primaryAction,
        List<ReviewNextAction> actions,
        List<String> reasonCodes
    ) {
    }
}
