package com.adp.gateway.egress.application;

import java.util.List;

import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseSensitiveFinding;

public interface ResponseLeakageDetector {

    String detectorVersion();

    List<ResponseSensitiveFinding> detect(OutboundCandidatePayload outboundPayload, Object responsePayload);
}
