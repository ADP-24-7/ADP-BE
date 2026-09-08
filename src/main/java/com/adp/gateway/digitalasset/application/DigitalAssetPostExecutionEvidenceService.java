package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionEvidence;
import com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.stereotype.Service;

@Service
public class DigitalAssetPostExecutionEvidenceService {
    private final TransactionDetailResolver transactionDetailResolver;
    private final ReceiptFinalityResolver receiptFinalityResolver;
    private final TokenTransferResolver tokenTransferResolver;
    private final InternalTraceResolver internalTraceResolver;
    private final ExactExecutionAmountResolver exactExecutionAmountResolver;

    public DigitalAssetPostExecutionEvidenceService(
        TransactionDetailResolver transactionDetailResolver,
        ReceiptFinalityResolver receiptFinalityResolver,
        TokenTransferResolver tokenTransferResolver,
        InternalTraceResolver internalTraceResolver,
        ExactExecutionAmountResolver exactExecutionAmountResolver
    ) {
        this.transactionDetailResolver = transactionDetailResolver;
        this.receiptFinalityResolver = receiptFinalityResolver;
        this.tokenTransferResolver = tokenTransferResolver;
        this.internalTraceResolver = internalTraceResolver;
        this.exactExecutionAmountResolver = exactExecutionAmountResolver;
    }

    public DigitalAssetPostExecutionEvidence resolve(
        String executionId,
        ExternalExecutionResult result,
        DigitalAssetReconciliationAssessment assessment,
        OffsetDateTime observedAt
    ) {
        var transaction = transactionDetailResolver.resolveTransaction(result);
        var receiptFinality = receiptFinalityResolver.resolveReceiptFinality(result);
        var transfer = tokenTransferResolver.resolveTokenTransfer(result);
        var exactAmount = exactExecutionAmountResolver.resolveAmount(result, transaction, transfer);
        DigitalAssetPostExecutionStatus status = status(
            result, transaction.transactionHash() != null, receiptFinality.isFinalSuccess(), transfer.present(),
            exactAmount.amount() != null && Objects.equals(exactAmount.amount(), result.executedAmount()),
            assessment.requiresReview()
        );
        return new DigitalAssetPostExecutionEvidence(
            executionId, status, result.externalStatus(), result.providerStatus(), result.receiptStatus(),
            result.finalityStatus(), exactAmount.source(), transaction.evidenceDigest(),
            receiptFinality.evidenceDigest(), transfer.evidenceDigest(),
            internalTraceResolver.resolveEvidenceDigest(result).orElse(null), exactAmount.evidenceDigest(),
            assessment.expectedProjectionDigest(), assessment.actualProjectionDigest(), assessment.mismatchedFields(),
            result.responseDigest(), observedAt
        );
    }

    private DigitalAssetPostExecutionStatus status(
        ExternalExecutionResult result,
        boolean transactionPresent,
        boolean finalReceipt,
        boolean transferPresent,
        boolean exactAmountResolved,
        boolean mismatch
    ) {
        if (result.externalStatus() == DigitalAssetExternalStatus.SENT_UNKNOWN) {
            return DigitalAssetPostExecutionStatus.SENT_UNKNOWN;
        }
        if (result.externalStatus() == DigitalAssetExternalStatus.FAILED) {
            return DigitalAssetPostExecutionStatus.FAILED;
        }
        if (!result.isFinalSuccess()) {
            return DigitalAssetPostExecutionStatus.PENDING;
        }
        if (!transactionPresent || !finalReceipt || !transferPresent || !exactAmountResolved || mismatch) {
            return DigitalAssetPostExecutionStatus.REVIEW_REQUIRED;
        }
        return DigitalAssetPostExecutionStatus.VERIFIED;
    }
}
