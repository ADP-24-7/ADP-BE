package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.ExecutionPackRequestScope;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.digitalasset.domain.ApprovedTransaction;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationFieldContract;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DigitalAssetPreExecutionGuardTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T00:00:00Z");
    private ApprovedTransactionResolver approvedResolver;
    private DigitalAssetRuntimeSnapshotService snapshotService;
    private DigitalAssetRuntimeSnapshotPersistence persistence;
    private DigitalAssetPreExecutionGuard guard;

    @BeforeEach
    void setUp() {
        approvedResolver = mock(ApprovedTransactionResolver.class);
        snapshotService = mock(DigitalAssetRuntimeSnapshotService.class);
        persistence = mock(DigitalAssetRuntimeSnapshotPersistence.class);
        guard = new DigitalAssetPreExecutionGuard(
            approvedResolver, new ApprovedTransactionBindingEvaluator(), snapshotService, persistence
        );
        when(approvedResolver.resolve(any())).thenReturn(approved("10000"));
        when(snapshotService.isPinnedCurrent(any(), any(), any())).thenReturn(true);
    }

    @Test
    void passesAllSixControlsAndPersistsEvidence() {
        var result = evaluate(destination(), destination(), outbound(fields()), provider(fields())).orElseThrow();

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.controlResults()).hasSize(6).allSatisfy((control, status) -> {
            assertThat(control).isInstanceOf(DigitalAssetArtifactControl.class);
            assertThat(status).isEqualTo("PASSED");
        });
        verify(persistence).savePreExecutionGuard(result);
    }

    @Test
    void blocksMissingRequiredAndModifiedExactFields() {
        List<OutboundCandidateField> invalid = new ArrayList<>(fields());
        invalid.removeIf(field -> field.path().endsWith("requestedAmount"));
        invalid.add(exact("$.input.outboundRequest.requestedAmount", "9999", "digest-modified"));
        DestinationProfile withMissingRequired = withContract(destination(), new DestinationFieldContract(
            "input.outboundRequest.requestedDestination", DataClass.TRANSACTION_IDENTIFIER,
            FieldObligation.REQUIRED_EXACT, true, true
        ));

        var result = evaluate(
            withMissingRequired, withMissingRequired, outbound(invalid), provider(invalid)
        ).orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.reasonCodes()).contains(
            ReasonCode.DIGITAL_ASSET_REQUIRED_OUTBOUND_FIELD_MISSING,
            ReasonCode.DIGITAL_ASSET_REQUIRED_EXACT_VIOLATION
        );
    }

    @Test
    void blocksExactFieldsThatEnterTheTransformSet() {
        List<OutboundCandidateField> invalid = new ArrayList<>(fields());
        invalid.removeIf(field -> field.path().endsWith("requestedAmount"));
        invalid.add(transformed("$.input.outboundRequest.requestedAmount", DataClass.FINANCIAL_AMOUNT, "10000"));

        var result = evaluate(destination(), destination(), outbound(invalid), provider(invalid)).orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.reasonCodes()).contains(
            ReasonCode.DIGITAL_ASSET_REQUIRED_EXACT_VIOLATION,
            ReasonCode.DIGITAL_ASSET_TRANSFORM_NOT_ALLOWED
        );
    }

    @Test
    void routesUnresolvedDestinationPayloadToReview() {
        ProviderRequestPayload invalid = new ProviderRequestPayload(
            "provider-1", "out-1", "provider", "schema", "provider-digest", 1,
            Map.of("externalRequestId", "provider-1", "schemaVersion", "schema",
                "transaction", Map.of("amount", "10000"))
        );

        var result = evaluate(destination(), destination(), outbound(fields()), invalid).orElseThrow();

        assertThat(result.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(result.reasonCodes()).contains(ReasonCode.DIGITAL_ASSET_DESTINATION_MAPPING_UNRESOLVED);
    }

    @Test
    void blocksChangedApprovalAndToctouSnapshotBeforeConnector() {
        when(approvedResolver.resolve(any())).thenReturn(approved("9000"));
        when(snapshotService.isPinnedCurrent(any(), any(), any())).thenReturn(false);

        var result = evaluate(destination(), destination(), outbound(fields()), provider(fields())).orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.reasonCodes()).contains(
            ReasonCode.DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED,
            ReasonCode.DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID,
            ReasonCode.DIGITAL_ASSET_TRACE_BINDING_INVALID
        );
    }

    private Optional<com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult> evaluate(
        DestinationProfile pinnedDestination,
        DestinationProfile currentDestination,
        OutboundCandidatePayload outbound,
        ProviderRequestPayload provider
    ) {
        PolicySnapshot policy = mock(PolicySnapshot.class);
        when(policy.policyVersion()).thenReturn("policy-v1");
        when(policy.snapshotDigest()).thenReturn("policy-digest");
        return guard.evaluate(
            "exec-1", "institution", "subject-digest", scope(), input(), context(), pinnedDestination,
            currentDestination, policy, policy, transform(outbound), outbound, provider, Optional.of(snapshot()), NOW
        );
    }

    private TransformResult transform(OutboundCandidatePayload outbound) {
        List<TransformFieldResult> fields = outbound.fields().stream().map(field -> new TransformFieldResult(
            field.path(), "request", field.path(), field.dataClass(), field.strategy(), "strategy-v1", "key-v1",
            "mapping-v1", "instruction-digest",
            field.path().endsWith("requestedAmount") ? "digest-amount" : "digest-customer",
            field.valueDigest(), field.strategy() == TransformStrategy.VAULT_TOKEN ? String.valueOf(field.value()) : null,
            field.value()
        )).toList();
        return new TransformResult("transform-1", true, "APPLIED", "transform-digest", fields);
    }

    private List<OutboundCandidateField> fields() {
        return List.of(
            transformed("$.input.customerId", DataClass.CUSTOMER_IDENTIFIER, "customer-token"),
            exact("$.input.outboundRequest.requestedAmount", "10000", "digest-amount")
        );
    }

    private OutboundCandidateField exact(String path, Object value, String digest) {
        return new OutboundCandidateField(
            path, DataClass.FINANCIAL_AMOUNT, TransformStrategy.KEEP, FieldObligation.REQUIRED_EXACT,
            FieldTreatment.KEEP_EXACT_PROTECTED, digest, List.of(), value
        );
    }

    private OutboundCandidateField transformed(String path, DataClass dataClass, Object value) {
        return new OutboundCandidateField(
            path, dataClass, TransformStrategy.VAULT_TOKEN, FieldObligation.PSEUDONYMIZABLE,
            FieldTreatment.TRANSFORMED, "digest-token", List.of(), value
        );
    }

    private OutboundCandidatePayload outbound(List<OutboundCandidateField> fields) {
        return new OutboundCandidatePayload(
            "out-1", "destination", "destination-v1", "destination-digest",
            ExecutionPackType.DIGITAL_ASSET, "schema", "outbound-digest", fields
        );
    }

    private ProviderRequestPayload provider(List<OutboundCandidateField> fields) {
        Map<String, Object> transaction = new HashMap<>();
        fields.forEach(field -> transaction.put(
            field.path().endsWith("customerId") ? "customerToken" : "amount", field.value()
        ));
        return new ProviderRequestPayload(
            "provider-1", "out-1", "provider", "schema", "provider-digest", transaction.size(),
            Map.of("externalRequestId", "provider-1", "schemaVersion", "schema", "transaction", transaction)
        );
    }

    private DestinationProfile destination() {
        return new DestinationProfile(
            "destination", "destination-v1", "destination-digest", "contract", "provider",
            ExecutionPackType.DIGITAL_ASSET, "schema", "tenant", "KR", "NONE", false,
            "ACTIVE", NOW.minusDays(1), null, List.of(new DestinationBinding("workload", "PURPOSE")),
            List.of(
                new DestinationFieldContract("input.customerId", DataClass.CUSTOMER_IDENTIFIER,
                    FieldObligation.PSEUDONYMIZABLE, true, false),
                new DestinationFieldContract("input.outboundRequest.requestedAmount", DataClass.FINANCIAL_AMOUNT,
                    FieldObligation.REQUIRED_EXACT, true, true)
            )
        );
    }

    private DestinationProfile withContract(DestinationProfile profile, DestinationFieldContract contract) {
        List<DestinationFieldContract> contracts = new ArrayList<>(profile.fieldContracts());
        contracts.add(contract);
        return new DestinationProfile(
            profile.destinationProfileId(), profile.profileVersion(), profile.profileDigest(), profile.contractVersion(),
            profile.providerProfileId(), profile.packType(), profile.schemaVersion(), profile.tenantId(), profile.region(),
            profile.retentionPolicy(), profile.trainingUseAllowed(), profile.status(), profile.effectiveAt(),
            profile.expiresAt(), profile.allowedBindings(), contracts
        );
    }

    private CanonicalContext context() {
        return new CanonicalContext(
            CanonicalContext.SCHEMA_VERSION, "ctx", "access", "workload", "PURPOSE", "customer",
            "subject-digest", List.of(
                new CanonicalContextField("$.input.customerId", "request", "customerId",
                    DataClass.CUSTOMER_IDENTIFIER, "customer", "digest-customer"),
                new CanonicalContextField("$.input.outboundRequest.requestedAmount", "request", "requestedAmount",
                    DataClass.FINANCIAL_AMOUNT, "10000", "digest-amount")
            ), Map.of("approvedTransactionDigest", "a".repeat(64)), "context-digest"
        );
    }

    private ApprovedTransaction approved(String amount) {
        return new ApprovedTransaction(
            "approved-1", "1.0.0", ("10000".equals(amount) ? "a" : "b").repeat(64),
            "institution", "subject-digest", "workload", "PURPOSE",
            "policy-v1", descriptor(), DigitalAssetAmount.fromWire(amount), null, "destination", "wallet",
            "beneficiary", NOW.minusDays(1), NOW.plusDays(1)
        );
    }

    private DigitalAssetRuntimeSnapshot snapshot() {
        return new DigitalAssetRuntimeSnapshot(
            "snapshot-1", "sha256:" + "1".repeat(64), "exec-1", "institution", "workload", "PURPOSE",
            "artifact", "1.0.0", "a".repeat(64), "policy:1", "policy-v1", "policy-digest",
            "destination", "destination-v1", "destination-digest", "control-v1", "sha256:" + "b".repeat(64),
            "crosswalk-v1", "sha256:" + "c".repeat(64), NOW
        );
    }

    private ExecutionPackRequestScope scope() {
        return new ExecutionPackRequestScope(
            "institution", "workload", "PURPOSE", "subject-digest", "destination", "idem", NOW
        );
    }

    private Map<String, Object> input() {
        return Map.of(
            "approvedTransactionReference", "approved-1", "customerId", "customer", "accountId", "account",
            "outboundRequest", Map.of(
                "requestedAsset", Map.of(
                    "chainId", "eip155:1", "assetKind", "FUNGIBLE_TOKEN", "assetSymbol", "ASSET",
                    "assetContractAddress", "0x0000000000000000000000000000000000000001", "operation", "TRANSFER"
                ),
                "requestedAmount", "10000", "requestedDestination", "wallet",
                "requestedBeneficiaryReference", "beneficiary", "regulatoryOutboundData", Map.of()
            )
        );
    }

    private DigitalAssetDescriptor descriptor() {
        return new DigitalAssetDescriptor(
            "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
            "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
        );
    }
}
