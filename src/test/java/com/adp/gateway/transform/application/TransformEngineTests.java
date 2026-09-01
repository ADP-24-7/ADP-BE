package com.adp.gateway.transform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TransformEngineTests {

    private final CanonicalValueHasher hasher = new CanonicalValueHasher();

    @Test
    void appliesEveryTransformStrategyWithoutExposingRawValuesInResultDigest() {
        TransformEngine engine = new TransformEngine(
            context -> instruction(strategyFor(context.fieldPath())),
            request -> "vault_tok_test",
            keyVersion -> new PseudonymizationKey(
                keyVersion,
                new SecretKeySpec("test-hmac-key-material-32-bytes".getBytes(), "HmacSHA256")
            ),
            hasher,
            new SimpleMeterRegistry()
        );

        var result = engine.transform("exec_test", context(), policyContext(), decision());

        assertThat(result.applied()).isTrue();
        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.outputDigest()).hasSize(64);
        assertThat(strategy(result, "fields.masked")).isEqualTo(TransformStrategy.MASK);
        assertThat(strategy(result, "fields.hmac")).isEqualTo(TransformStrategy.HMAC_PSEUDO);
        assertThat(strategy(result, "fields.vault")).isEqualTo(TransformStrategy.VAULT_TOKEN);
        assertThat(strategy(result, "fields.removed")).isEqualTo(TransformStrategy.REMOVE);
        assertThat(strategy(result, "fields.kept")).isEqualTo(TransformStrategy.KEEP);
        assertThat(strategy(result, "fields.generalized")).isEqualTo(TransformStrategy.GENERALIZE);
        assertThat(strategy(result, "fields.separated")).isEqualTo(TransformStrategy.FIELD_SEPARATION);
        assertThat(field(result, "fields.vault").tokenRef()).startsWith("vault_tok_");
        assertThat(field(result, "fields.hmac").keyVersion()).isEqualTo("test-key-v1");
        assertThat(field(result, "fields.hmac").strategyVersion()).isEqualTo("test-strategy-v1");
        assertThat(field(result, "fields.hmac").mappingVersion()).isEqualTo("test-mapping-v1");
        assertThat(field(result, "fields.hmac").instructionDigest()).hasSize(64);
        assertThat(field(result, "fields.hmac").transformedValueDigest())
            .isNotEqualTo(hasher.hash("HMAC_PSEUDO:" + field(result, "fields.hmac").sourceValueDigest()));
    }

    @Test
    void instructionParametersAffectTransformDigest() {
        TransformEngine bucket1000Engine = engineWithGeneralizeBucket("1000");
        TransformEngine bucket10000Engine = engineWithGeneralizeBucket("10000");

        var first = bucket1000Engine.transform("exec_test", generalizeContext(), policyContext(), decision());
        var second = bucket10000Engine.transform("exec_test", generalizeContext(), policyContext(), decision());

        assertThat(second.outputDigest()).isNotEqualTo(first.outputDigest());
        assertThat(field(second, "fields.generalized").instructionDigest())
            .isNotEqualTo(field(first, "fields.generalized").instructionDigest());
    }

    @Test
    void tokenTtlAffectsInstructionDigest() {
        TransformEngine oneHourTtlEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.GENERALIZE,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of("bucketSize", "1000")
        ));
        TransformEngine thirtyDayTtlEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.GENERALIZE,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofDays(30),
            Map.of("bucketSize", "1000")
        ));

        var first = oneHourTtlEngine.transform("exec_test", generalizeContext(), policyContext(), decision());
        var second = thirtyDayTtlEngine.transform("exec_test", generalizeContext(), policyContext(), decision());

        assertThat(field(second, "fields.generalized").instructionDigest())
            .isNotEqualTo(field(first, "fields.generalized").instructionDigest());
    }

    @Test
    void transformScopeSeparatesVaultAndHmacAcrossPurposes() {
        TransformEngine vaultEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.VAULT_TOKEN,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of()
        ));
        TransformEngine hmacEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.HMAC_PSEUDO,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of()
        ));

        var supportVault = vaultEngine.transform("exec_test", vaultContext(), policyContext("CUSTOMER_SUPPORT"), decision());
        var riskVault = vaultEngine.transform("exec_test", vaultContext(), policyContext("RISK_ANALYSIS"), decision());
        var supportHmac = hmacEngine.transform("exec_test", hmacContext(), policyContext("CUSTOMER_SUPPORT"), decision());
        var riskHmac = hmacEngine.transform("exec_test", hmacContext(), policyContext("RISK_ANALYSIS"), decision());

        assertThat(field(riskVault, "fields.vault").tokenRef())
            .isNotEqualTo(field(supportVault, "fields.vault").tokenRef());
        assertThat(field(riskHmac, "fields.hmac").transformedValueDigest())
            .isNotEqualTo(field(supportHmac, "fields.hmac").transformedValueDigest());
    }

    @Test
    void transformScopeDoesNotRotateForPolicyProvenanceOnlyChanges() {
        TransformEngine vaultEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.VAULT_TOKEN,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of()
        ));
        TransformEngine hmacEngine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.HMAC_PSEUDO,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of()
        ));

        var policyV1Vault = vaultEngine.transform("exec_test", vaultContext(), policyContext(), decision("policy-v1", "snapshot-v1"));
        var policyV2Vault = vaultEngine.transform("exec_test", vaultContext(), policyContext(), decision("policy-v2", "snapshot-v2"));
        var policyV1Hmac = hmacEngine.transform("exec_test", hmacContext(), policyContext(), decision("policy-v1", "snapshot-v1"));
        var policyV2Hmac = hmacEngine.transform("exec_test", hmacContext(), policyContext(), decision("policy-v2", "snapshot-v2"));

        assertThat(field(policyV2Vault, "fields.vault").tokenRef())
            .isEqualTo(field(policyV1Vault, "fields.vault").tokenRef());
        assertThat(field(policyV2Hmac, "fields.hmac").transformedValueDigest())
            .isEqualTo(field(policyV1Hmac, "fields.hmac").transformedValueDigest());
    }

    @Test
    void invalidInstructionParametersFailClosed() {
        TransformEngine invalidGeneralize = engineWithInstruction(new TransformInstruction(
            TransformStrategy.GENERALIZE,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of("bucketSize", "0")
        ));
        TransformEngine invalidMask = engineWithInstruction(new TransformInstruction(
            TransformStrategy.MASK,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of("visibleSuffix", "-1")
        ));
        TransformEngine invalidVault = engineWithInstruction(new TransformInstruction(
            TransformStrategy.VAULT_TOKEN,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ZERO,
            Map.of()
        ));

        assertThatThrownBy(() -> invalidGeneralize.transform("exec_test", generalizeContext(), policyContext(), decision()))
            .isInstanceOf(TransformResolutionException.class)
            .hasMessageContaining("bucketSize");
        assertThatThrownBy(() -> invalidMask.transform("exec_test", maskContext(), policyContext(), decision()))
            .isInstanceOf(TransformResolutionException.class)
            .hasMessageContaining("visibleSuffix");
        assertThatThrownBy(() -> invalidVault.transform("exec_test", vaultContext(), policyContext(), decision()))
            .isInstanceOf(TransformResolutionException.class)
            .hasMessageContaining("tokenTtl");
    }

    @Test
    void invalidGeneralizeInputFailsClosed() {
        TransformEngine engine = engineWithInstruction(new TransformInstruction(
            TransformStrategy.GENERALIZE,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of("bucketSize", "1000")
        ));

        assertThatThrownBy(() -> engine.transform("exec_test", invalidGeneralizeContext(), policyContext(), decision()))
            .isInstanceOf(TransformInputException.class)
            .hasMessageContaining("GENERALIZE input must be numeric");
    }

    private TransformStrategy strategyFor(String path) {
        return switch (path) {
            case "fields.masked" -> TransformStrategy.MASK;
            case "fields.hmac" -> TransformStrategy.HMAC_PSEUDO;
            case "fields.vault" -> TransformStrategy.VAULT_TOKEN;
            case "fields.removed" -> TransformStrategy.REMOVE;
            case "fields.kept" -> TransformStrategy.KEEP;
            case "fields.generalized" -> TransformStrategy.GENERALIZE;
            case "fields.separated" -> TransformStrategy.FIELD_SEPARATION;
            default -> throw new IllegalArgumentException(path);
        };
    }

    private TransformInstruction instruction(TransformStrategy strategy) {
        return new TransformInstruction(
            strategy,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of()
        );
    }

    private TransformEngine engineWithGeneralizeBucket(String bucketSize) {
        return engineWithInstruction(new TransformInstruction(
            TransformStrategy.GENERALIZE,
            "test-strategy-v1",
            "test-key-v1",
            "test-mapping-v1",
            Duration.ofHours(1),
            Map.of("bucketSize", bucketSize)
        ));
    }

    private TransformEngine engineWithInstruction(TransformInstruction instruction) {
        return new TransformEngine(
            context -> instruction,
            request -> "vault_tok_" + request.mappingScope(),
            keyVersion -> new PseudonymizationKey(
                keyVersion,
                new SecretKeySpec("test-hmac-key-material-32-bytes".getBytes(), "HmacSHA256")
            ),
            hasher,
            new SimpleMeterRegistry()
        );
    }

    private TransformFieldResult field(com.adp.gateway.transform.domain.TransformResult result, String path) {
        return result.fields().stream()
            .filter(field -> path.equals(field.path()))
            .findFirst()
            .orElseThrow();
    }

    private TransformStrategy strategy(com.adp.gateway.transform.domain.TransformResult result, String path) {
        return field(result, path).strategy();
    }

    private CanonicalContext context() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_test",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(
                field("fields.masked", "01012345678", DataClass.CUSTOMER_IDENTIFIER),
                field("fields.hmac", "txn-1", DataClass.TRANSACTION_IDENTIFIER),
                field("fields.vault", "customer-1", DataClass.CUSTOMER_IDENTIFIER),
                field("fields.removed", "unknown", DataClass.UNKNOWN),
                field("fields.kept", "metadata", DataClass.BUSINESS_METADATA),
                field("fields.generalized", "12580.90", DataClass.FINANCIAL_AMOUNT),
                field("fields.separated", "account-1", DataClass.ACCOUNT_IDENTIFIER)
            ),
            "canonical_digest"
        );
    }

    private CanonicalContext generalizeContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_generalize",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(field("fields.generalized", "12580.90", DataClass.FINANCIAL_AMOUNT)),
            "canonical_digest"
        );
    }

    private CanonicalContext maskContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_mask",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(field("fields.masked", "01012345678", DataClass.CUSTOMER_IDENTIFIER)),
            "canonical_digest"
        );
    }

    private CanonicalContext vaultContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_vault",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(field("fields.vault", "customer-1", DataClass.CUSTOMER_IDENTIFIER)),
            "canonical_digest"
        );
    }

    private CanonicalContextField field(String path, Object value, DataClass dataClass) {
        return new CanonicalContextField(path, "dataset", "field", dataClass, value, hasher.hash(String.valueOf(value)));
    }

    private RuntimePolicyContext policyContext() {
        return policyContext("CUSTOMER_SUPPORT");
    }

    private RuntimePolicyContext policyContext(String purpose) {
        return new RuntimePolicyContext(
            "customer_summary",
            purpose,
            "customer",
            "subject_digest",
            "canonical_digest",
            List.of(DataClass.CUSTOMER_IDENTIFIER),
            List.of("AI_USE"),
            "internal-provider",
            "input_digest",
            "runtime_digest"
        );
    }

    private CanonicalContext hmacContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_hmac",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(field("fields.hmac", "txn-1", DataClass.TRANSACTION_IDENTIFIER)),
            "canonical_digest"
        );
    }

    private CanonicalContext invalidGeneralizeContext() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION,
            "ctx_generalize_invalid",
            "da_test",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject_digest",
            List.of(field("fields.generalized", "INVALID_AMOUNT", DataClass.FINANCIAL_AMOUNT)),
            "canonical_digest"
        );
    }

    private RuntimeDecision decision() {
        return decision("policy-v1", "snapshot_digest");
    }

    private RuntimeDecision decision(String policyVersion, String snapshotDigest) {
        return new RuntimeDecision(
            "decision_test",
            PolicyAction.TRANSFORM,
            FinalAction.TRANSFORM,
            List.of(ReasonCode.POLICY_ALLOW),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            policyVersion,
            snapshotDigest,
            "runtime_digest",
            new SourcePolicyEvaluationArtifactRef(
                "artifact",
                "v1",
                new ArtifactDigest("sha256", "digest")
            )
        );
    }
}
