package com.adp.gateway.policyharness.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.domain.AiModelProfile;
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
    public static final String DIGITAL_ASSET_APPROVAL_REFERENCE = "approval_digital_asset_purchase_v1";
    private final SubjectRefHasher subjectRefHasher;
    private final AiModelProfileCatalog aiModelProfiles;

    public ProjectProvisionalApprovalScopeAdapter(
        SubjectRefHasher subjectRefHasher,
        AiModelProfileCatalog aiModelProfiles
    ) {
        this.subjectRefHasher = subjectRefHasher;
        this.aiModelProfiles = aiModelProfiles;
    }

    @Override
    public ApprovalScope load(String approvalReference, OffsetDateTime requestStartedAt) {
        var modelProfile = aiModelProfiles.profiles().stream()
            .filter(profile -> aiModelProfiles.approvalReference(profile).equals(approvalReference))
            .findFirst();
        if (modelProfile.isPresent()) {
            return aiEvaluationApproval(modelProfile.get());
        }
        if (DIGITAL_ASSET_APPROVAL_REFERENCE.equals(approvalReference)) {
            return digitalAssetApproval();
        }
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

    private ApprovalScope aiEvaluationApproval(AiModelProfile modelProfile) {
        String subjectDigest = subjectRefHasher.hash(SubjectRef.from("customer:customer-100"));
        return new ApprovalScope(
            aiModelProfiles.approvalReference(modelProfile), modelProfile.profileVersion(),
            aiModelProfiles.approvalScopeDigest(modelProfile, subjectDigest),
            "institution_local", "institution-policy/local/1.0.0", "local-institution-policy-digest-v1",
            "customer_summary", "CUSTOMER_SUPPORT", "EXACT_DIGEST",
            subjectDigest,
            "be-runtime-policy/0.0.0", aiModelProfiles.policySnapshotDigest(),
            Set.of(AdpRole.RUNTIME_EXECUTOR), Set.of("AI_USE", "CUSTOMER_SUPPORT"),
            Set.of(
                "request.prompt", "customer.customer_id", "customer.segment", "account.account_id",
                "account.account_type", "account.balance", "transaction.transaction_id",
                "transaction.posted_at", "transaction.merchant_category", "transaction.amount"
            ),
            modelProfile.destinationProfileId(), modelProfile.destinationProfileVersion(),
            OffsetDateTime.parse("2026-09-07T00:00:00Z"), null,
            List.of("PROJECT_PROVISIONAL_AI_EVALUATION_APPROVAL_EVIDENCE")
        );
    }

    private ApprovalScope digitalAssetApproval() {
        return new ApprovalScope(
            DIGITAL_ASSET_APPROVAL_REFERENCE, "1.0.0", "local-digital-asset-approval-digest-v1",
            "institution_local", "institution-policy/local/1.0.0", "local-institution-policy-digest-v1",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "EXACT_DIGEST",
            subjectRefHasher.hash(SubjectRef.from("customer:customer-100")),
            "be-runtime-policy/0.0.0",
            "be-snapshot-local-fixture:digital-asset-purchase:mock-asset-platform",
            Set.of(AdpRole.RUNTIME_EXECUTOR), Set.of("DIGITAL_ASSET"),
            Set.of("request.customerId", "request.accountId", "request.walletAddress", "request.assetId",
                "request.amount", "request.beneficiaryReference"),
            "dest_mock_asset_platform_v1", "1.0.0", OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null, List.of("PROJECT_PROVISIONAL_DIGITAL_ASSET_APPROVAL_EVIDENCE")
        );
    }
}
