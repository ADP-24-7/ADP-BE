package com.adp.gateway.digitalasset.infrastructure;

import java.util.Optional;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.application.ExactExecutionAmountResolver;
import com.adp.gateway.digitalasset.application.InternalTraceResolver;
import com.adp.gateway.digitalasset.application.ReceiptFinalityResolver;
import com.adp.gateway.digitalasset.application.TokenTransferResolver;
import com.adp.gateway.digitalasset.application.TransactionDetailResolver;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.ExactExecutionAmountEvidence;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.ReceiptFinalityEvidence;
import com.adp.gateway.digitalasset.domain.TransactionDetailEvidence;
import com.adp.gateway.digitalasset.domain.TransferExecutionEvidence;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ProviderResponsePostExecutionResolver implements TransactionDetailResolver, ReceiptFinalityResolver,
    TokenTransferResolver, InternalTraceResolver, ExactExecutionAmountResolver {

    private final CanonicalValueHasher hasher;

    public ProviderResponsePostExecutionResolver(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public TransactionDetailEvidence resolveTransaction(ExternalExecutionResult result) {
        DigitalAssetDescriptor asset = result.executedAssetKind() == null ? null : new DigitalAssetDescriptor(
            result.executedChainId(), result.executedAssetKind(), result.executedAssetSymbol(),
            result.executedAssetContractAddress(), result.operation(), result.tokenId()
        );
        return new TransactionDetailEvidence(
            result.transactionHash(), asset, result.executedRecipientAddress(), result.nativeValue(), result.executedAt(),
            digest("transaction", result.transactionHash(), result.executedChainId(), result.executedRecipientAddress(),
                result.executedAssetKind(), result.executedAssetSymbol(), result.executedAssetContractAddress(),
                result.nativeValue(), result.operation(), result.tokenId(), result.executedAt())
        );
    }

    @Override
    public ReceiptFinalityEvidence resolveReceiptFinality(ExternalExecutionResult result) {
        return new ReceiptFinalityEvidence(
            result.receiptStatus(), result.finalityStatus(), result.finalizedAt(),
            digest("receipt-finality", result.transactionHash(), result.receiptStatus(), result.finalityStatus(),
                result.finalizedAt())
        );
    }

    @Override
    public TransferExecutionEvidence resolveTokenTransfer(ExternalExecutionResult result) {
        boolean required = result.executedAssetKind() != null && result.executedAssetKind() != DigitalAssetKind.NATIVE;
        boolean present = !required || result.tokenTransferEvidenceRef() != null;
        return new TransferExecutionEvidence(
            required, present, required ? result.executedAmount() : null,
            digest("transfer", required, present, result.tokenTransferEvidenceRef(), result.executedAmount())
        );
    }

    @Override
    public Optional<String> resolveEvidenceDigest(ExternalExecutionResult result) {
        return Optional.ofNullable(result.internalTraceEvidenceRef())
            .map(reference -> digest("internal-trace", reference));
    }

    @Override
    public ExactExecutionAmountEvidence resolveAmount(
        ExternalExecutionResult result,
        TransactionDetailEvidence transaction,
        TransferExecutionEvidence transfer
    ) {
        boolean nativeAsset = result.executedAssetKind() == DigitalAssetKind.NATIVE;
        var amount = nativeAsset ? transaction.nativeValue() : transfer.amount();
        String source = nativeAsset ? "TRANSACTION_VALUE" : "TOKEN_TRANSFER";
        return new ExactExecutionAmountEvidence(
            amount, source, digest("exact-amount", source, amount)
        );
    }

    private String digest(Object... values) {
        return hasher.hash(java.util.Arrays.stream(values)
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining("|")));
    }
}
