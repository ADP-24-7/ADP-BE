package com.adp.gateway.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.adp.gateway.ai.domain.AiEvaluationContractSnapshot;

public interface AiEvaluationContractPort {
    Optional<AiEvaluationContractSnapshot> load(String runId);
    AiEvaluationContractSnapshot freeze(String runId, AiEvaluationContractSnapshot snapshot);
    void bind(String executionId, String runId, String caseId, String digest,
              String modelProfileDigest, String decisionId, String transformExecutionId,
              String outboundPayloadId, String providerRequestDigest, String providerInputDigest);
    Map<String, Object> evidence(String runId, List<String> executionIds);
}
