package com.adp.gateway.policyharness.infrastructure;

import java.time.OffsetDateTime;

import com.adp.gateway.policyharness.application.ApprovalScopeNotFoundException;
import com.adp.gateway.policyharness.application.ApprovalScopePort;
import com.adp.gateway.policyharness.domain.ApprovalScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredApprovalScopeAdapter implements ApprovalScopePort {

    @Override
    public ApprovalScope load(String approvalReference, OffsetDateTime requestStartedAt) {
        throw new ApprovalScopeNotFoundException(approvalReference);
    }
}
