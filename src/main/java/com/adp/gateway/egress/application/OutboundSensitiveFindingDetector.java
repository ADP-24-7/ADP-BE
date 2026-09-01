package com.adp.gateway.egress.application;

import java.util.List;

import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundSensitiveFinding;

public interface OutboundSensitiveFindingDetector {

    List<OutboundSensitiveFinding> detect(OutboundCandidateField field);
}
