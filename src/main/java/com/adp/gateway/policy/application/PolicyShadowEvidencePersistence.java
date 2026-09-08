package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.PolicyShadowEvidence;

public interface PolicyShadowEvidencePersistence {
    PolicyShadowEvidence save(PolicyShadowEvidence evidence);
}
