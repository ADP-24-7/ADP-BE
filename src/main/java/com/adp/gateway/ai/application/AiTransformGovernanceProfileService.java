package com.adp.gateway.ai.application;

import java.util.List;

import com.adp.gateway.ai.api.AiTransformGovernanceProfileResponse;
import com.adp.gateway.ai.api.AiTransformGovernanceProfileResponse.FieldControl;
import com.adp.gateway.ai.api.AiTransformGovernanceProfileResponse.ProviderGovernance;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiTransformGovernanceProfileService {
    public static final String E2_HANDOFF_DIGEST =
        "sha256:899cf31a920c1363cfb21b9c7d6f3204819222935bcbb9008a01ccbf9a8ba73e";
    public static final String E3_PROFILE_DIGEST =
        "sha256:788b2da13d17ca13d5e1062ce98dce143d54810ed9dee31976f398b04a03fa0d";
    private final AiEvaluationContractService evaluationContracts;

    public AiTransformGovernanceProfileService(AiEvaluationContractService evaluationContracts) {
        this.evaluationContracts = evaluationContracts;
    }
    public AiTransformGovernanceProfileResponse read(AuthPrincipal principal, String runId) {
        authorize(principal);
        if (!AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID.equals(runId)) {
            throw new AiEvaluationRunMismatchException("AI_TRANSFORM_GOVERNANCE_PROFILE_NOT_FOUND");
        }
        return new AiTransformGovernanceProfileResponse(
            runId,
            "customer_summary",
            "Synthetic customer account and recent-transaction summary",
            "BANKING_CUSTOMER_SUPPORT",
            "CUSTOMER_SUPPORT",
            "Produce an internal, human-reviewed factual summary for an authorized customer-support context",
            "ONE_AUTHORIZED_SYNTHETIC_CUSTOMER",
            "GENERATE_INTERNAL_SUPPORT_SUMMARY",
            "AUTHORIZED_CUSTOMER_SUPPORT_OPERATOR_OR_EVALUATION_HARNESS",
            E2_HANDOFF_DIGEST,
            "1.1.1",
            "1.2.0",
            E3_PROFILE_DIGEST,
            "VALIDATED",
            "ACTIVATED",
            "E2_POLICY_REQUIREMENT_VALIDATED",
            "PROVIDER_GOVERNANCE_BLOCKED",
            "PENDING_EXTERNAL_EXECUTION",
            false,
            providerGovernance(),
            fields(),
            List.of()
        );
    }

    private ProviderGovernance providerGovernance() {
        var contract = evaluationContracts.providerGovernanceContract();
        return new ProviderGovernance(
            contract.providerConnectionProfileId(), contract.modelProfileIds(), contract.allowedRegions(),
            "KR", "UNRESOLVED", "BLOCK", "PROVIDER_REGION_REQUIRED",
            contract.approvedRetentionMode(), contract.maximumRetentionDays(),
            "SESSION_END_DEFAULT_WITH_SECURITY_EXCEPTION", null, "UNVERIFIED",
            "BLOCK", "RETENTION_UNVERIFIED", contract.allowedReusePurposes(),
            List.of("REQUEST_EXECUTION", "SECURITY_FRAUD_ABUSE_MONITORING", "AI_MODEL_IMPROVEMENT"),
            "BLOCK", "MODEL_TRAINING_NOT_ALLOWED", "BLOCK",
            List.of("PROVIDER_REGION_REQUIRED", "RETENTION_UNVERIFIED", "MODEL_TRAINING_NOT_ALLOWED"),
            contract.contractVersion(), contract.contractDigest(), contract.activationStatus()
        );
    }

    private List<FieldControl> fields() {
        return List.of(
            exact("input.prompt", "BUSINESS_INSTRUCTION", "Defines the frozen summary task", "KEEP", true),
            identifier("customer.customer_id", "SYNTHETIC_CUSTOMER_IDENTIFIER",
                "Correlates the authorized subject with retrieved accounts", "VAULT_TOKEN"),
            exact("customer.segment", "SYNTHETIC_BUSINESS_METADATA",
                "States the customer segment used in the factual support summary", "KEEP", true),
            identifier("account.account_id", "SYNTHETIC_ACCOUNT_IDENTIFIER",
                "Links each authorized account to its transactions", "VAULT_TOKEN"),
            exact("account.account_type", "SYNTHETIC_FINANCIAL_METADATA",
                "Identifies the account category being summarized", "KEEP", true),
            exact("account.balance", "SYNTHETIC_FINANCIAL_AMOUNT",
                "Provides the factual current balance in the authorized summary", "KEEP", true),
            identifier("transaction.transaction_id", "SYNTHETIC_TRANSACTION_IDENTIFIER",
                "Keeps repeated trace references to the same transaction stable", "HMAC_PSEUDO"),
            exact("transaction.posted_at", "SYNTHETIC_TEMPORAL_METADATA",
                "Establishes recency and ordering inside the frozen 90-day window", "KEEP", true),
            exact("transaction.merchant_category", "SYNTHETIC_BUSINESS_METADATA",
                "Provides transaction context without free-text description", "KEEP", true),
            exact("transaction.amount", "SYNTHETIC_FINANCIAL_AMOUNT",
                "Provides the exact transaction fact used in the summary", "KEEP", true),
            removed("customer.customer_name", "Composite customer name is unnecessary for the summary", "REMOVE"),
            removed("customer.first_name", "Customer first name is unnecessary for the summary", "REMOVE"),
            removed("customer.last_name", "Customer last name is unnecessary for the summary", "REMOVE"),
            removed("customer.date_of_birth", "Date of birth is unnecessary for the summary", "REMOVE"),
            removed("customer.address", "Address is unnecessary for the summary", "REMOVE"),
            removed("customer.phone_number", "Phone number is unnecessary for the summary", "REMOVE"),
            removed("customer.email", "Email is unnecessary for the summary", "REMOVE"),
            removed("customer.resident_registration_number", "Resident registration number is prohibited", "PROHIBITED_EXTERNAL"),
            removed("account.account_number", "Raw account number is unnecessary", "REMOVE"),
            removed("transaction.description", "Free-text description has uncontrolled leakage risk", "REMOVE")
        );
    }

    private FieldControl exact(String field, String classification, String need, String current, boolean release) {
        boolean requirementMatch = "KEEP".equals(current);
        return new FieldControl(field, classification, need, "REQUIRED_EXACT",
            List.of("EXACT_VALUE_PRESERVE", "PURPOSE_LIMIT"),
            List.of("EXACT_MATCH", "ABSOLUTE_ERROR", "THRESHOLD_DECISION_PRESERVATION"),
            List.of("KEEP"), List.of("GENERALIZE", "MASK", "HMAC_PSEUDO", "VAULT_TOKEN", "REMOVE"),
            current, "KEEP", "KEEP", "PASS", "PASS", "NOT_APPLICABLE", "PASS",
            "SUPPORTED_ACTIVE", "FIELD_CONTRACT_PASS_PROVIDER_GOVERNANCE_SEPARATELY_BLOCKED",
            List.of("SHINHAN-PRO-INVESTOR", "WOO-2009-UTILITY"), requirementMatch, release, "CONDITIONAL_SYNTHETIC_ONLY",
            "E2_REQUIREMENT_FROZEN", evidence(field));
    }

    private FieldControl identifier(String field, String classification, String need, String current) {
        return new FieldControl(field, classification, need, "RELATION_PRESERVE",
            List.of("IDENTITY_HIDE", "RELATION_PRESERVE", "REVERSIBILITY_CONTROL"),
            List.of("DIRECT_IDENTIFIER_EXPOSURE", "COLLISION_RATE", "FALSE_MATCH_RATE", "REFERENTIAL_INTEGRITY"),
            List.of("MASK", "HMAC_PSEUDO", "VAULT_TOKEN"), List.of("KEEP", "GENERALIZE", "REMOVE"),
            current, "APPROVED_CANDIDATE_REQUIRED", current, "PASS", "PASS", "PASS", "NOT_APPLICABLE",
            "SUPPORTED_ACTIVE", "FIELD_CONTRACT_PASS_PROVIDER_GOVERNANCE_SEPARATELY_BLOCKED",
            current.equals("HMAC_PSEUDO")
                ? List.of("NIST-FIPS-198-1", "NIST-IR-8053", "PIPC-PSEUDONYM-GUIDE-2026")
                : List.of("PCI-TOKENIZATION-2015", "PIPC-PSEUDONYM-GUIDE-2026", "SHINHAN-PRIVACY-CONTROLS"),
            List.of("MASK", "HMAC_PSEUDO", "VAULT_TOKEN").contains(current),
            true, "CONDITIONAL_SYNTHETIC_ONLY", "E2_REQUIREMENT_FROZEN", evidence(field));
    }

    private FieldControl removed(String field, String need, String requirement) {
        return new FieldControl(field, "SENSITIVE_OR_UNNECESSARY", need, requirement,
            List.of("DATA_MINIMIZATION", "DESTINATION_LIMIT", "PURPOSE_LIMIT"),
            List.of("FIELD_ABSENCE", "SCHEMA_VALIDITY"), List.of("REMOVE"),
            List.of("KEEP", "MASK", "HMAC_PSEUDO", "VAULT_TOKEN", "GENERALIZE"),
            "REMOVE", "REMOVE", "REMOVE", "PASS", "PASS", "NOT_APPLICABLE", "NOT_APPLICABLE",
            "SUPPORTED_ACTIVE", "FIELD_CONTRACT_PASS_PROVIDER_GOVERNANCE_SEPARATELY_BLOCKED",
            List.of("FSC-FINANCIAL-DEID-2022", "PIPC-PSEUDONYM-GUIDE-2026", "NIST-SP-800-188"),
            true, false, "CONDITIONAL_SYNTHETIC_ONLY",
            "E2_REQUIREMENT_FROZEN", evidence(field));
    }

    private String evidence(String field) {
        return "E2_TO_E3_TRANSFORM_REQUIREMENTS.json#" + field;
    }

    private void authorize(AuthPrincipal principal) {
        if (!"institution_local".equals(principal.institutionId())
            || !(principal.workloadIds().contains("*") || principal.workloadIds().contains("customer_summary"))) {
            throw new AccessDeniedException("Transform governance profile scope denied");
        }
    }
}
