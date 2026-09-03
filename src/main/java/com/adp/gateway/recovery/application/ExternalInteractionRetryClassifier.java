package com.adp.gateway.recovery.application;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalFailureCategory;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.springframework.stereotype.Component;

@Component
public class ExternalInteractionRetryClassifier {

    public RetryDisposition classify(ConnectorStatus status, ExternalFailureCategory failureCategory) {
        return switch (status) {
            case SENT_UNKNOWN -> RetryDisposition.RECONCILE_FIRST;
            case ACKNOWLEDGED, COMPLETED -> RetryDisposition.NO_RETRY;
            case NOT_SENT -> failureCategory == ExternalFailureCategory.TRANSIENT_PRE_SEND
                ? RetryDisposition.RETRY_ALLOWED
                : RetryDisposition.MANUAL_REVIEW;
            case FAILED -> failureCategory == ExternalFailureCategory.TRANSIENT_PRE_SEND
                ? RetryDisposition.RETRY_ALLOWED
                : RetryDisposition.MANUAL_REVIEW;
        };
    }
}
