package com.adp.gateway.egress.application;

import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;

public interface ExternalSchemaMapper {

    ExecutionPackType supportedPack();

    ProviderRequestPayload map(DestinationProfile destinationProfile, OutboundCandidatePayload outboundPayload);
}
