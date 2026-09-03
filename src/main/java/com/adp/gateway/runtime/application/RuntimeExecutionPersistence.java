package com.adp.gateway.runtime.application;

import java.util.Optional;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policyharness.domain.PolicyHarnessBinding;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.transform.domain.TransformResult;

public interface RuntimeExecutionPersistence {

    void recordReceived(
        RuntimeExecutionTrace trace,
        String idempotencyInstitutionId,
        String requestHash
    );

    Optional<IdempotentExecutionReplay> findIdempotentExecution(
        String institutionId,
        String workloadId,
        String idempotencyKey
    );

    void recordDestinationProfile(String executionId, DestinationProfile destinationProfile);

    void recordPolicyEvaluation(String executionId, PolicySnapshot snapshot);

    void recordRuntimeDecision(String executionId, RuntimeDecision decision);

    void recordTransform(String executionId, RuntimeDecision decision, TransformResult transformResult);

    void recordOutbound(String executionId, OutboundCandidatePayload payload, OutboundGuardResult guardResult);

    void recordPolicyHarness(String executionId, PolicyHarnessBinding binding);

    void recordProviderRequest(
        String executionId,
        DestinationProfile destinationProfile,
        ProviderRequestPayload providerRequest
    );

    void recordConnector(String executionId, ConnectorResult connectorResult);

    void recordResponseGuard(String executionId, ConnectorResult connectorResult, ResponseGuardResult responseGuardResult);

    void recordRetrieved(String executionId, CanonicalContext context);

    void recordAuthorization(String executionId, String authorizationStatus);

    void recordControlledDelivery(String executionId, ControlledDeliveryResult result);

    void updateStatus(String executionId, RuntimeExecutionStatus status);

    RuntimeExecutionTrace load(String executionId);
}
