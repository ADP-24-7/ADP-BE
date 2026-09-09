package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.PolicyShadowEvidence;
import org.springframework.stereotype.Component;

@Component
public class PolicyShadowApprovalPolicy {
    public static final String VERSION = "policy-shadow-approval/1.0.0";
    private static final String REQUIRED_CASE_ID = "GOLDEN_ALLOW";
    private static final String REQUIRED_CASE_VERSION = "1.0.0";

    public void validate(PolicyShadowEvidence evidence) {
        if (!REQUIRED_CASE_ID.equals(evidence.evaluationCaseId())
            || !REQUIRED_CASE_VERSION.equals(evidence.evaluationCaseVersion())) {
            throw new PolicyLifecycleException("POLICY_SHADOW_CASE_NOT_APPROVABLE");
        }
        if (!"MATCH".equals(evidence.result())) {
            throw new PolicyLifecycleException("POLICY_SHADOW_DIFF_NOT_APPROVABLE");
        }
    }
}
