package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.ExecutionPackRequestScope;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeInput;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;
import com.adp.gateway.egress.domain.DestinationFieldContract;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.FieldObligation;
import com.adp.gateway.egress.domain.FieldTreatment;
import com.adp.gateway.egress.domain.OutboundCandidateField;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.transform.domain.TransformStrategy;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetPreExecutionGuard {
    private static final Set<String> PROVIDER_PAYLOAD_FIELDS = Set.of(
        "externalRequestId", "schemaVersion", "transaction"
    );
    private static final Map<String, String> PROVIDER_FIELDS = Map.ofEntries(
        Map.entry("$.input.customerId", "customerToken"),
        Map.entry("$.input.accountId", "accountToken"),
        Map.entry("$.input.outboundRequest.requestedAsset.chainId", "chainId"),
        Map.entry("$.input.outboundRequest.requestedAsset.assetKind", "assetKind"),
        Map.entry("$.input.outboundRequest.requestedAsset.assetSymbol", "assetSymbol"),
        Map.entry("$.input.outboundRequest.requestedAsset.assetContractAddress", "assetContractAddress"),
        Map.entry("$.input.outboundRequest.requestedAsset.operation", "operation"),
        Map.entry("$.input.outboundRequest.requestedAsset.tokenId", "tokenId"),
        Map.entry("$.input.outboundRequest.requestedAmount", "amount"),
        Map.entry("$.input.outboundRequest.requestedDestination", "recipientAddress")
    );

    private final ApprovedTransactionResolver approvedTransactionResolver;
    private final ApprovedTransactionBindingEvaluator bindingEvaluator;
    private final DigitalAssetRuntimeSnapshotService snapshotService;
    private final DigitalAssetRuntimeSnapshotPersistence persistence;

    public DigitalAssetPreExecutionGuard(
        ApprovedTransactionResolver approvedTransactionResolver,
        ApprovedTransactionBindingEvaluator bindingEvaluator,
        DigitalAssetRuntimeSnapshotService snapshotService,
        DigitalAssetRuntimeSnapshotPersistence persistence
    ) {
        this.approvedTransactionResolver = approvedTransactionResolver;
        this.bindingEvaluator = bindingEvaluator;
        this.snapshotService = snapshotService;
        this.persistence = persistence;
    }

    public Optional<DigitalAssetPreExecutionGuardResult> evaluate(
        String executionId,
        String institutionId,
        String subjectRefDigest,
        ExecutionPackRequestScope requestScope,
        Map<String, Object> input,
        CanonicalContext context,
        DestinationProfile pinnedDestination,
        DestinationProfile currentDestination,
        PolicySnapshot pinnedPolicy,
        PolicySnapshot currentPolicy,
        TransformResult transformResult,
        OutboundCandidatePayload outbound,
        ProviderRequestPayload providerRequest,
        Optional<DigitalAssetRuntimeSnapshot> pinnedSnapshot,
        OffsetDateTime evaluatedAt
    ) {
        if (pinnedDestination.packType() != ExecutionPackType.DIGITAL_ASSET) {
            return Optional.empty();
        }

        Map<DigitalAssetArtifactControl, String> controls = new EnumMap<>(DigitalAssetArtifactControl.class);
        List<ReasonCode> reasons = new ArrayList<>();
        DigitalAssetRuntimeInput runtimeInput = DigitalAssetRuntimeInput.from(input, requestScope);
        List<ReasonCode> bindingReasons = new ArrayList<>();
        try {
            var approved = approvedTransactionResolver.resolve(new ApprovedTransactionLookup(
                runtimeInput.approvedTransactionReference(), institutionId, subjectRefDigest,
                requestScope.workloadId(), requestScope.purpose()
            ));
            bindingReasons.addAll(bindingEvaluator.evaluate(approved, runtimeInput.outboundRequest()));
            if (!approved.digest().equals(context.trustedMetadata().get("approvedTransactionDigest"))) {
                bindingReasons.add(ReasonCode.DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID);
            }
        } catch (ApprovedTransactionUnavailableException exception) {
            bindingReasons.add(ReasonCode.DIGITAL_ASSET_APPROVED_TRANSACTION_NOT_FOUND);
        }
        record(controls, reasons, DigitalAssetArtifactControl.APPROVED_VS_REQUESTED_MATCH,
            bindingReasons, "BLOCKED");

        List<ReasonCode> presenceReasons = requiredFieldReasons(pinnedDestination, outbound);
        record(controls, reasons, DigitalAssetArtifactControl.REQUIRED_OUTBOUND_FIELD_PRESENCE,
            presenceReasons, "BLOCKED");

        List<ReasonCode> exactReasons = exactPreservationReasons(
            pinnedDestination, context, transformResult, outbound
        );
        record(controls, reasons, DigitalAssetArtifactControl.REQUIRED_EXACT_PRESERVATION,
            exactReasons, "BLOCKED");

        List<ReasonCode> separationReasons = transformSeparationReasons(pinnedDestination, outbound);
        record(controls, reasons, DigitalAssetArtifactControl.TRANSFORM_FIELD_SEPARATION,
            separationReasons, "BLOCKED");

        DestinationPayloadAssessment destinationAssessment = destinationPayloadAssessment(
            pinnedDestination, outbound, providerRequest
        );
        record(controls, reasons, DigitalAssetArtifactControl.DESTINATION_SPECIFIC_PAYLOAD,
            destinationAssessment.reasons(), destinationAssessment.failureStatus());

        List<ReasonCode> traceReasons = traceBindingReasons(
            executionId, requestScope, pinnedDestination, currentDestination, pinnedPolicy, currentPolicy,
            outbound, providerRequest, pinnedSnapshot
        );
        record(controls, reasons, DigitalAssetArtifactControl.TRACE_BINDING, traceReasons, "BLOCKED");

        String status = controls.containsValue("BLOCKED") ? "BLOCKED"
            : controls.containsValue("REVIEW_REQUIRED") ? "REVIEW_REQUIRED" : "PASSED";
        DigitalAssetPreExecutionGuardResult result = new DigitalAssetPreExecutionGuardResult(
            executionId,
            pinnedSnapshot.map(DigitalAssetRuntimeSnapshot::snapshotId).orElse(null),
            status,
            controls,
            reasons.stream().distinct().toList(),
            outbound.candidatePayloadDigest(),
            providerRequest.canonicalPayloadDigest(),
            evaluatedAt
        );
        persistence.savePreExecutionGuard(result);
        return Optional.of(result);
    }

    private List<ReasonCode> requiredFieldReasons(
        DestinationProfile destination,
        OutboundCandidatePayload outbound
    ) {
        boolean missing = destination.fieldContracts().stream()
            .filter(DestinationFieldContract::required)
            .anyMatch(contract -> outbound.fields().stream().noneMatch(field -> matches(field.path(), contract.path())));
        return missing ? List.of(ReasonCode.DIGITAL_ASSET_REQUIRED_OUTBOUND_FIELD_MISSING) : List.of();
    }

    private List<ReasonCode> exactPreservationReasons(
        DestinationProfile destination,
        CanonicalContext context,
        TransformResult transformResult,
        OutboundCandidatePayload outbound
    ) {
        Map<String, CanonicalContextField> source = new HashMap<>();
        context.fields().forEach(field -> source.put(field.path(), field));
        for (DestinationFieldContract contract : destination.fieldContracts()) {
            if (!isExact(contract.obligation())) {
                continue;
            }
            Optional<OutboundCandidateField> candidate = outbound.fields().stream()
                .filter(field -> matches(field.path(), contract.path()))
                .findFirst();
            if (candidate.isEmpty() && contract.required()) {
                return List.of(ReasonCode.DIGITAL_ASSET_REQUIRED_EXACT_VIOLATION);
            }
            if (candidate.isEmpty()) {
                continue;
            }
            CanonicalContextField original = source.get(candidate.get().path());
            var transformed = transformResult.fields().stream()
                .filter(field -> field.path().equals(candidate.get().path()))
                .findFirst();
            if (original == null
                || transformed.isEmpty()
                || candidate.get().strategy() != TransformStrategy.KEEP
                || candidate.get().treatment() != FieldTreatment.KEEP_EXACT_PROTECTED
                || transformed.get().strategy() != TransformStrategy.KEEP
                || !original.valueDigest().equals(transformed.get().sourceValueDigest())
                || !candidate.get().valueDigest().equals(transformed.get().transformedValueDigest())
                || !java.util.Objects.equals(original.value(), candidate.get().value())) {
                return List.of(ReasonCode.DIGITAL_ASSET_REQUIRED_EXACT_VIOLATION);
            }
        }
        return List.of();
    }

    private List<ReasonCode> transformSeparationReasons(
        DestinationProfile destination,
        OutboundCandidatePayload outbound
    ) {
        boolean invalid = outbound.fields().stream().anyMatch(field -> destination.fieldContract(field.path())
            .map(contract -> isExact(contract.obligation())
                ? field.strategy() != TransformStrategy.KEEP
                : field.strategy() == TransformStrategy.KEEP || field.treatment() == FieldTreatment.KEEP_EXACT_PROTECTED)
            .orElse(true));
        return invalid ? List.of(ReasonCode.DIGITAL_ASSET_TRANSFORM_NOT_ALLOWED) : List.of();
    }

    private DestinationPayloadAssessment destinationPayloadAssessment(
        DestinationProfile destination,
        OutboundCandidatePayload outbound,
        ProviderRequestPayload providerRequest
    ) {
        Map<String, Object> expected = new HashMap<>();
        for (OutboundCandidateField field : outbound.fields()) {
            String providerField = PROVIDER_FIELDS.get(field.path());
            if (providerField == null || expected.containsKey(providerField)) {
                return DestinationPayloadAssessment.mappingUnresolved();
            }
            expected.put(providerField, field.value());
        }
        Object transactionValue = providerRequest.payload().get("transaction");
        if (!(transactionValue instanceof Map<?, ?> transaction)
            || !providerRequest.payload().keySet().equals(PROVIDER_PAYLOAD_FIELDS)
            || !transaction.keySet().equals(expected.keySet())
            || !destination.providerProfileId().equals(providerRequest.providerProfileId())
            || !destination.schemaVersion().equals(providerRequest.schemaVersion())
            || providerRequest.fieldCount() != expected.size()) {
            return DestinationPayloadAssessment.mappingUnresolved();
        }
        boolean valueMismatch = expected.entrySet().stream()
            .anyMatch(entry -> !Objects.equals(entry.getValue(), transaction.get(entry.getKey())));
        if (valueMismatch) {
            return DestinationPayloadAssessment.payloadMismatch();
        }
        return DestinationPayloadAssessment.passed();
    }

    private List<ReasonCode> traceBindingReasons(
        String executionId,
        ExecutionPackRequestScope scope,
        DestinationProfile pinnedDestination,
        DestinationProfile currentDestination,
        PolicySnapshot pinnedPolicy,
        PolicySnapshot currentPolicy,
        OutboundCandidatePayload outbound,
        ProviderRequestPayload providerRequest,
        Optional<DigitalAssetRuntimeSnapshot> pinnedSnapshot
    ) {
        boolean invalid = !snapshotService.isPinnedCurrent(pinnedSnapshot, currentDestination, currentPolicy)
            || pinnedSnapshot.stream().anyMatch(snapshot -> !snapshot.executionId().equals(executionId)
                || !snapshot.institutionId().equals(scope.institutionId())
                || !snapshot.workloadId().equals(scope.workloadId())
                || !snapshot.purposeCode().equals(scope.purpose()))
            || !sameDestination(pinnedDestination, currentDestination)
            || !pinnedPolicy.policyVersion().equals(currentPolicy.policyVersion())
            || !pinnedPolicy.snapshotDigest().equals(currentPolicy.snapshotDigest())
            || !outbound.outboundPayloadId().equals(providerRequest.outboundPayloadId())
            || !outbound.destinationProfileId().equals(pinnedDestination.destinationProfileId())
            || !outbound.destinationProfileVersion().equals(pinnedDestination.profileVersion())
            || !outbound.destinationProfileDigest().equals(pinnedDestination.profileDigest())
            || !providerRequest.providerRequestId().equals(providerRequest.payload().get("externalRequestId"))
            || !providerRequest.schemaVersion().equals(providerRequest.payload().get("schemaVersion"));
        return invalid ? List.of(ReasonCode.DIGITAL_ASSET_TRACE_BINDING_INVALID) : List.of();
    }

    private boolean sameDestination(DestinationProfile left, DestinationProfile right) {
        return left.destinationProfileId().equals(right.destinationProfileId())
            && left.profileVersion().equals(right.profileVersion())
            && left.profileDigest().equals(right.profileDigest());
    }

    private boolean matches(String fieldPath, String contractPath) {
        return fieldPath.equals(contractPath) || fieldPath.endsWith("." + contractPath);
    }

    private boolean isExact(FieldObligation obligation) {
        return obligation == FieldObligation.REQUIRED_EXACT
            || obligation == FieldObligation.CONDITIONAL_EXACT;
    }

    private void record(
        Map<DigitalAssetArtifactControl, String> controls,
        List<ReasonCode> allReasons,
        DigitalAssetArtifactControl control,
        List<ReasonCode> controlReasons,
        String failureStatus
    ) {
        controls.put(control, controlReasons.isEmpty() ? "PASSED" : failureStatus);
        allReasons.addAll(controlReasons);
    }

    private record DestinationPayloadAssessment(List<ReasonCode> reasons, String failureStatus) {
        private static DestinationPayloadAssessment passed() {
            return new DestinationPayloadAssessment(List.of(), "PASSED");
        }

        private static DestinationPayloadAssessment mappingUnresolved() {
            return new DestinationPayloadAssessment(
                List.of(ReasonCode.DIGITAL_ASSET_DESTINATION_MAPPING_UNRESOLVED), "REVIEW_REQUIRED"
            );
        }

        private static DestinationPayloadAssessment payloadMismatch() {
            return new DestinationPayloadAssessment(
                List.of(ReasonCode.DIGITAL_ASSET_CONTRACT_GAP), "BLOCKED"
            );
        }
    }
}
