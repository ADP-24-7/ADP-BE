package com.adp.gateway.policyharness.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.dataaccess.application.SubjectRefHasher;
import com.adp.gateway.policyharness.application.ApprovalScopeNotFoundException;
import com.adp.gateway.policyharness.application.ApprovalScopePort;
import com.adp.gateway.policyharness.domain.ApprovalScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalApprovalScopeAdapter implements ApprovalScopePort {

    public static final String APPROVAL_REFERENCE = "approval_ai_customer_support_v1";
    private final SubjectRefHasher subjectRefHasher;

    public ProjectProvisionalApprovalScopeAdapter(SubjectRefHasher subjectRefHasher) {
        this.subjectRefHasher = subjectRefHasher;
    }

    @Override
    public ApprovalScope load(String approvalReference, OffsetDateTime requestStartedAt) {
        if (!APPROVAL_REFERENCE.equals(approvalReference)) {
            throw new ApprovalScopeNotFoundException(approvalReference);
        }
        return new ApprovalScope(
            APPROVAL_REFERENCE,
            "1.0.0",
            "local-approval-scope-digest-v1",
            "institution_local",
            "institution-policy/local/1.0.0",
            "local-institution-policy-digest-v1",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "EXACT_DIGEST",
            subjectRefHasher.hash(SubjectRef.from("customer:customer-100")),
            "be-runtime-policy/0.0.0",
            "be-snapshot-local-fixture:customer-summary:customer-support:internal-provider",
            Set.of(AdpRole.RUNTIME_EXECUTOR),
            Set.of("AI_USE", "CUSTOMER_SUPPORT"),
            Set.of(
                "request.prompt",
                "customer.customer_id",
                "customer.segment",
                "account.account_id",
                "account.account_type",
                "account.balance",
                "transaction.transaction_id",
                "transaction.posted_at",
                "transaction.merchant_category",
                "transaction.amount"
            ),
            "dest_internal_provider_project_provisional",
            "0.0.0",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of("PROJECT_PROVISIONAL_APPROVAL_EVIDENCE")
        );
    }
}
