package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalFailureCategory;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.junit.jupiter.api.Test;

class ExternalInteractionRetryClassifierTests {

    private final ExternalInteractionRetryClassifier classifier = new ExternalInteractionRetryClassifier();

    @Test
    void sentUnknownAlwaysRequiresReconciliationBeforeRetry() {
        assertThat(classifier.classify(ConnectorStatus.SENT_UNKNOWN, ExternalFailureCategory.UNKNOWN))
            .isEqualTo(RetryDisposition.RECONCILE_FIRST);
    }

    @Test
    void acknowledgedAndCompletedInteractionsAreNeverRetried() {
        assertThat(classifier.classify(ConnectorStatus.ACKNOWLEDGED, ExternalFailureCategory.TRANSIENT_PRE_SEND))
            .isEqualTo(RetryDisposition.NO_RETRY);
        assertThat(classifier.classify(ConnectorStatus.COMPLETED, ExternalFailureCategory.TRANSIENT_PRE_SEND))
            .isEqualTo(RetryDisposition.NO_RETRY);
    }

    @Test
    void onlyExplicitPreSendTransientFailureCanBeRetried() {
        assertThat(classifier.classify(ConnectorStatus.NOT_SENT, ExternalFailureCategory.TRANSIENT_PRE_SEND))
            .isEqualTo(RetryDisposition.RETRY_ALLOWED);
        assertThat(classifier.classify(ConnectorStatus.FAILED, ExternalFailureCategory.UNKNOWN))
            .isEqualTo(RetryDisposition.MANUAL_REVIEW);
    }
}
