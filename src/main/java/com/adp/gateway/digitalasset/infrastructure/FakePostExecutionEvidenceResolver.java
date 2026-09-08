package com.adp.gateway.digitalasset.infrastructure;

import java.util.Optional;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.application.ExactExecutionAmountResolver;
import com.adp.gateway.digitalasset.application.InternalTraceResolver;
import com.adp.gateway.digitalasset.application.ReceiptFinalityResolver;
import com.adp.gateway.digitalasset.application.TokenTransferResolver;
import com.adp.gateway.digitalasset.application.TransactionDetailResolver;
import com.adp.gateway.digitalasset.domain.DigitalAssetEvidenceSourceType;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.ExactExecutionAmountEvidence;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.ReceiptFinalityEvidence;
import com.adp.gateway.digitalasset.domain.TransactionDetailEvidence;
import com.adp.gateway.digitalasset.domain.TransferExecutionEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakePostExecutionEvidenceResolver implements TransactionDetailResolver, ReceiptFinalityResolver,
    TokenTransferResolver, InternalTraceResolver, ExactExecutionAmountResolver {

    private final CanonicalValueHasher hasher;
    private final FakeDigitalAssetPlatformStateStore stateStore;

    public FakePostExecutionEvidenceResolver(
        CanonicalValueHasher hasher,
        FakeDigitalAssetPlatformStateStore stateStore
    ) {
        this.hasher = hasher;
        this.stateStore = stateStore;
    }

    @Override
    public TransactionDetailEvidence resolveTransaction(ExternalExecutionResult result) {
        var value = observation(result);
        return new TransactionDetailEvidence(
            value.transactionHash(), value.asset(), value.recipientAddress(), value.nativeValue(), value.executedAt(),
            digest("transaction", value.transactionHash(), value.asset(), value.recipientAddress(),
                value.nativeValue(), value.executedAt())
        );
    }

    @Override
    public ReceiptFinalityEvidence resolveReceiptFinality(ExternalExecutionResult result) {
        var value = observation(result);
        return new ReceiptFinalityEvidence(
            value.receiptStatus(), value.finalityStatus(), value.finalizedAt(),
            digest("receipt-finality", value.transactionHash(), value.receiptStatus(), value.finalityStatus(),
                value.finalizedAt())
        );
    }

    @Override
    public TransferExecutionEvidence resolveTokenTransfer(ExternalExecutionResult result) {
        var value = observation(result);
        boolean required = value.asset().assetKind() != DigitalAssetKind.NATIVE;
        boolean present = !required || value.transferReference() != null;
        return new TransferExecutionEvidence(
            required, present, required ? value.transferredAmount() : null,
            digest("transfer", value.transactionHash(), required, present, value.transferReference(),
                value.transferredAmount())
        );
    }

    @Override
    public Optional<String> resolveEvidenceDigest(ExternalExecutionResult result) {
        var value = observation(result);
        return Optional.ofNullable(value.internalTraceReference())
            .map(reference -> digest("internal-trace", value.transactionHash(), reference));
    }

    @Override
    public ExactExecutionAmountEvidence resolveAmount(
        ExternalExecutionResult result,
        TransactionDetailEvidence transaction,
        TransferExecutionEvidence transfer
    ) {
        boolean nativeAsset = transaction.asset().assetKind() == DigitalAssetKind.NATIVE;
        var amount = nativeAsset ? transaction.nativeValue() : transfer.amount();
        String source = nativeAsset ? "TRANSACTION_VALUE" : "TOKEN_TRANSFER";
        return new ExactExecutionAmountEvidence(amount, source, digest("exact-amount", source, amount));
    }

    @Override
    public DigitalAssetEvidenceSourceType sourceType() {
        return DigitalAssetEvidenceSourceType.INDEPENDENT_EXTERNAL;
    }

    private FakeDigitalAssetExecutionObservation observation(ExternalExecutionResult result) {
        return stateStore.findExecution(result.externalReference())
            .orElseThrow(() -> new IllegalStateException("Independent digital asset evidence is unavailable"));
    }

    private String digest(Object... values) {
        return hasher.hash(java.util.Arrays.stream(values)
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining("|")));
    }
}
