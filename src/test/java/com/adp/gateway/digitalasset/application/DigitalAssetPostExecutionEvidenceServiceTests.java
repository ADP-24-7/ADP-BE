package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetFinalityStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetProviderStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReceiptStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.infrastructure.FakeDigitalAssetPlatformStateStore;
import com.adp.gateway.digitalasset.infrastructure.FakePostExecutionEvidenceResolver;
import com.adp.gateway.digitalasset.infrastructure.ProviderResponsePostExecutionResolver;
import org.junit.jupiter.api.Test;

class DigitalAssetPostExecutionEvidenceServiceTests {
    private static final OffsetDateTime EXECUTED_AT = OffsetDateTime.parse("2026-09-09T00:00:00Z");
    private final CanonicalValueHasher hasher = new CanonicalValueHasher();
    private final DigitalAssetReconciliationEvaluator evaluator = new DigitalAssetReconciliationEvaluator(hasher);
    private final FakeDigitalAssetPlatformStateStore stateStore = new FakeDigitalAssetPlatformStateStore();
    private final FakePostExecutionEvidenceResolver resolver = new FakePostExecutionEvidenceResolver(hasher, stateStore);
    private final DigitalAssetPostExecutionEvidenceService service = new DigitalAssetPostExecutionEvidenceService(
        resolver, resolver, resolver, resolver, resolver, evaluator
    );

    @Test
    void verifiesTokenTransferEvenWhenNativeTransactionValueIsZero() {
        ExternalExecutionResult result = result(
            DigitalAssetExternalStatus.SETTLED, DigitalAssetReceiptStatus.SUCCESS,
            DigitalAssetFinalityStatus.FINALIZED, "token-transfer:1"
        );
        stateStore.recordExecution(result);

        var evidence = service.resolve(
            "exec-1", result, evaluator.evaluate(request(), result), EXECUTED_AT.plusMinutes(1)
        );

        assertThat(evidence.status()).isEqualTo(DigitalAssetPostExecutionStatus.VERIFIED);
        assertThat(evidence.amountSource()).isEqualTo("TOKEN_TRANSFER");
        assertThat(result.nativeValue()).isEqualTo(DigitalAssetAmount.from("0"));
        assertThat(evidence.transferEvidenceDigest()).matches("[0-9a-f]{64}");
    }

    @Test
    void requiresReviewWhenSettledTokenResultHasNoTransferEvidence() {
        ExternalExecutionResult result = result(
            DigitalAssetExternalStatus.SETTLED, DigitalAssetReceiptStatus.SUCCESS,
            DigitalAssetFinalityStatus.FINALIZED, null
        );
        stateStore.recordExecution(result);

        var evidence = service.resolve(
            "exec-1", result, evaluator.evaluate(request(), result), EXECUTED_AT.plusMinutes(1)
        );

        assertThat(evidence.status()).isEqualTo(DigitalAssetPostExecutionStatus.REVIEW_REQUIRED);
    }

    @Test
    void keepsTransactionHashOnlyResultPendingUntilReceiptAndFinalityAreKnown() {
        ExternalExecutionResult result = result(
            DigitalAssetExternalStatus.SETTLING, DigitalAssetReceiptStatus.PENDING,
            DigitalAssetFinalityStatus.UNCONFIRMED, "token-transfer:pending"
        );
        stateStore.recordExecution(result);

        var evidence = service.resolve(
            "exec-1", result, evaluator.evaluate(request(), result), EXECUTED_AT.plusMinutes(1)
        );

        assertThat(evidence.status()).isEqualTo(DigitalAssetPostExecutionStatus.PENDING);
        assertThat(evidence.permitsCompletion()).isFalse();
    }

    @Test
    void preservesTypedSentUnknownForRecovery() {
        ExternalExecutionResult result = result(
            DigitalAssetExternalStatus.SENT_UNKNOWN, DigitalAssetReceiptStatus.NOT_AVAILABLE,
            DigitalAssetFinalityStatus.UNCONFIRMED, null
        );
        stateStore.recordExecution(result);

        var evidence = service.resolve(
            "exec-1", result, evaluator.evaluate(request(), result), EXECUTED_AT.plusMinutes(1)
        );

        assertThat(evidence.status()).isEqualTo(DigitalAssetPostExecutionStatus.SENT_UNKNOWN);
        assertThat(evidence.permitsCompletion()).isFalse();
    }

    @Test
    void providerDerivedEvidenceCannotVerifyOrComplete() {
        var providerResolver = new ProviderResponsePostExecutionResolver(hasher);
        var providerService = new DigitalAssetPostExecutionEvidenceService(
            providerResolver, providerResolver, providerResolver, providerResolver, providerResolver, evaluator
        );
        ExternalExecutionResult result = result(
            DigitalAssetExternalStatus.SETTLED, DigitalAssetReceiptStatus.SUCCESS,
            DigitalAssetFinalityStatus.FINALIZED, "token-transfer:provider"
        );

        var evidence = providerService.resolve(
            "exec-provider", result, evaluator.evaluate(request(), result), EXECUTED_AT.plusMinutes(1)
        );

        assertThat(evidence.status()).isEqualTo(DigitalAssetPostExecutionStatus.REVIEW_REQUIRED);
        assertThat(evidence.permitsCompletion()).isFalse();
    }

    @Test
    void detectsDisagreementBetweenProviderResponseAndIndependentEvidence() {
        ExternalExecutionResult providerResult = result(
            DigitalAssetExternalStatus.SETTLED, DigitalAssetReceiptStatus.SUCCESS,
            DigitalAssetFinalityStatus.FINALIZED, "token-transfer:provider"
        );
        ExternalExecutionResult independentlyObserved = new ExternalExecutionResult(
            providerResult.externalRequestId(), providerResult.externalReference(), providerResult.transactionHash(),
            providerResult.externalStatus(), providerResult.providerStatus(), providerResult.receiptStatus(),
            providerResult.finalityStatus(), providerResult.executedChainId(), providerResult.executedRecipientAddress(),
            providerResult.executedAssetKind(), providerResult.executedAssetSymbol(),
            providerResult.executedAssetContractAddress(), DigitalAssetAmount.from("999"),
            providerResult.nativeValue(), providerResult.operation(), providerResult.tokenId(),
            providerResult.tokenTransferEvidenceRef(), providerResult.internalTraceEvidenceRef(),
            providerResult.executedAt(), providerResult.finalizedAt(), "b".repeat(64)
        );
        stateStore.recordExecution(independentlyObserved);

        var resolution = service.resolve(
            "exec-disagreement", request(), providerResult, EXECUTED_AT.plusMinutes(1)
        );

        assertThat(resolution.evidence().status()).isEqualTo(DigitalAssetPostExecutionStatus.REVIEW_REQUIRED);
        assertThat(resolution.assessment().mismatchedFields()).containsExactly(DigitalAssetMismatchField.AMOUNT);
    }

    private Map<String, Object> request() {
        return Map.of("transaction", Map.of(
            "chainId", "eip155:1", "recipientAddress", "wallet-1", "assetKind", "FUNGIBLE_TOKEN",
            "assetSymbol", "ASSET", "assetContractAddress", "0x0000000000000000000000000000000000000001",
            "amount", "100", "operation", "TRANSFER"
        ));
    }

    private ExternalExecutionResult result(
        DigitalAssetExternalStatus externalStatus,
        DigitalAssetReceiptStatus receiptStatus,
        DigitalAssetFinalityStatus finalityStatus,
        String transferReference
    ) {
        boolean settled = externalStatus == DigitalAssetExternalStatus.SETTLED;
        return new ExternalExecutionResult(
            "request-1", "external-1", "0xabc", externalStatus, DigitalAssetProviderStatus.ACKNOWLEDGED,
            receiptStatus, finalityStatus, "eip155:1", "wallet-1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
            "0x0000000000000000000000000000000000000001", DigitalAssetAmount.from("100"),
            DigitalAssetAmount.from("0"), DigitalAssetOperation.TRANSFER, null, transferReference, null,
            EXECUTED_AT, settled ? EXECUTED_AT.plusMinutes(1) : null, "a".repeat(64)
        );
    }
}
