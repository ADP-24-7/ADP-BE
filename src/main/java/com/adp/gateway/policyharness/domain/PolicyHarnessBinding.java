package com.adp.gateway.policyharness.domain;

import java.util.List;

public record PolicyHarnessBinding(
    String institutionId,
    String approvalReference,
    String approvalVersion,
    String approvalScopeDigest,
    ApprovalReuseStatus approvalReuseStatus,
    List<String> reasonCodes,
    List<PolicyLayerReference> policyLayers,
    String policyLayersDigest,
    FieldLineage fieldLineage
) {

    public PolicyHarnessBinding {
        reasonCodes = List.copyOf(reasonCodes);
        policyLayers = List.copyOf(policyLayers);
    }

    public boolean permitsEgress() {
        return approvalReuseStatus == ApprovalReuseStatus.REUSE_ALLOWED
            || approvalReuseStatus == ApprovalReuseStatus.TRANSFORM_REQUIRED;
    }

    public PolicyHarnessBinding withFieldLineage(FieldLineage replacement) {
        return new PolicyHarnessBinding(
            institutionId,
            approvalReference,
            approvalVersion,
            approvalScopeDigest,
            approvalReuseStatus,
            reasonCodes,
            policyLayers,
            policyLayersDigest,
            replacement
        );
    }
}
