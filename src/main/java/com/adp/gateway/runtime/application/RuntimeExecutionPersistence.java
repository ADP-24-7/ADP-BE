package com.adp.gateway.runtime.application;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public interface RuntimeExecutionPersistence {

    void recordReceived(RuntimeExecutionTrace trace);

    void recordPolicyEvaluation(String executionId, PolicySnapshot snapshot);

    void recordRuntimeDecision(String executionId, RuntimeDecision decision);

    void recordRetrieved(String executionId, CanonicalContext context);

    void updateStatus(String executionId, RuntimeExecutionStatus status);

    RuntimeExecutionTrace load(String executionId);
}
