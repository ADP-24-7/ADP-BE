package com.adp.gateway.egress.application;

import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.ai.domain.AiEvaluationReference;

public interface ExternalSchemaMapper {

    ExecutionPackType supportedPack();

    ProviderRequestPayload map(
        String executionId,
        AiEvaluationReference evaluationReference,
        DestinationProfile destinationProfile,
        OutboundCandidatePayload outboundPayload
    );
}
