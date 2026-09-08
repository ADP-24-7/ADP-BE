package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

import com.adp.gateway.digitalasset.domain.DigitalAssetEvidenceSourceType;
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
    private final DigitalAssetReconciliationEvaluator reconciliationEvaluator;

    public DigitalAssetPostExecutionEvidenceService(
        TransactionDetailResolver transactionDetailResolver,
        ReceiptFinalityResolver receiptFinalityResolver,
        TokenTransferResolver tokenTransferResolver,
        InternalTraceResolver internalTraceResolver,
        ExactExecutionAmountResolver exactExecutionAmountResolver,
        DigitalAssetReconciliationEvaluator reconciliationEvaluator
    ) {
        this.transactionDetailResolver = transactionDetailResolver;
        this.receiptFinalityResolver = receiptFinalityResolver;
        this.tokenTransferResolver = tokenTransferResolver;
        this.internalTraceResolver = internalTraceResolver;
        this.exactExecutionAmountResolver = exactExecutionAmountResolver;
        this.reconciliationEvaluator = reconciliationEvaluator;
    }

    public DigitalAssetPostExecutionResolution resolve(
        String executionId,
        Map<String, Object> requestPayload,
        ExternalExecutionResult result,
        OffsetDateTime observedAt
    ) {
        var resolved = resolveEvidence(result);
        var assessment = reconciliationEvaluator.evaluate(
            requestPayload, projection(resolved.transaction(), resolved.exactAmount()),
            result.externalStatus() == DigitalAssetExternalStatus.SETTLED && resolved.receiptFinality().isFinalSuccess()
        );
        return new DigitalAssetPostExecutionResolution(
            evidence(executionId, result, assessment, observedAt, resolved), assessment
        );
    }

    public DigitalAssetPostExecutionEvidence resolve(
        String executionId,
        ExternalExecutionResult result,
        DigitalAssetReconciliationAssessment assessment,
        OffsetDateTime observedAt
    ) {
        return evidence(executionId, result, assessment, observedAt, resolveEvidence(result));
    }

    private ResolvedEvidence resolveEvidence(ExternalExecutionResult result) {
        var transaction = transactionDetailResolver.resolveTransaction(result);
        var receiptFinality = receiptFinalityResolver.resolveReceiptFinality(result);
        var transfer = tokenTransferResolver.resolveTokenTransfer(result);
        var exactAmount = exactExecutionAmountResolver.resolveAmount(result, transaction, transfer);
        return new ResolvedEvidence(
            transaction, receiptFinality, transfer, exactAmount,
            internalTraceResolver.resolveEvidenceDigest(result).orElse(null)
        );
    }

    private DigitalAssetPostExecutionEvidence evidence(
        String executionId,
        ExternalExecutionResult result,
        DigitalAssetReconciliationAssessment assessment,
        OffsetDateTime observedAt,
        ResolvedEvidence resolved
    ) {
        DigitalAssetEvidenceSourceType sourceType = sourceType();
        DigitalAssetPostExecutionStatus status = status(
            result, sourceType, resolved.transaction().transactionHash() != null,
            resolved.receiptFinality().isFinalSuccess(), resolved.transfer().present(),
            resolved.exactAmount().amount() != null, assessment.requiresReview()
        );
        return new DigitalAssetPostExecutionEvidence(
            executionId, status, sourceType, result.externalStatus(), result.providerStatus(),
            resolved.receiptFinality().receiptStatus(), resolved.receiptFinality().finalityStatus(),
            resolved.exactAmount().source(), resolved.transaction().evidenceDigest(),
            resolved.receiptFinality().evidenceDigest(), resolved.transfer().evidenceDigest(),
            resolved.internalTraceDigest(), resolved.exactAmount().evidenceDigest(),
            assessment.expectedProjectionDigest(), assessment.actualProjectionDigest(), assessment.mismatchedFields(),
            result.responseDigest(), observedAt
        );
    }

    private DigitalAssetPostExecutionStatus status(
        ExternalExecutionResult result,
        DigitalAssetEvidenceSourceType sourceType,
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
        if (sourceType != DigitalAssetEvidenceSourceType.INDEPENDENT_EXTERNAL
            || !transactionPresent || !finalReceipt || !transferPresent || !exactAmountResolved || mismatch) {
            return DigitalAssetPostExecutionStatus.REVIEW_REQUIRED;
        }
        return DigitalAssetPostExecutionStatus.VERIFIED;
    }

    private DigitalAssetEvidenceSourceType sourceType() {
        var sourceTypes = java.util.EnumSet.of(
            transactionDetailResolver.sourceType(), receiptFinalityResolver.sourceType(),
            tokenTransferResolver.sourceType(), internalTraceResolver.sourceType(), exactExecutionAmountResolver.sourceType()
        );
        return sourceTypes.size() == 1 ? sourceTypes.iterator().next() : DigitalAssetEvidenceSourceType.PROVIDER_RESPONSE;
    }

    private Map<String, Object> projection(
        com.adp.gateway.digitalasset.domain.TransactionDetailEvidence transaction,
        com.adp.gateway.digitalasset.domain.ExactExecutionAmountEvidence amount
    ) {
        Map<String, Object> projection = new TreeMap<>();
        if (transaction.asset() != null) {
            projection.put("chainId", transaction.asset().chainId());
            projection.put("assetKind", transaction.asset().assetKind().name());
            projection.put("assetSymbol", transaction.asset().assetSymbol());
            put(projection, "assetContractAddress", transaction.asset().assetContractAddress());
            projection.put("operation", transaction.asset().operation().name());
            put(projection, "tokenId", transaction.asset().tokenId());
        }
        put(projection, "recipientAddress", transaction.recipientAddress());
        put(projection, "amount", amount.amount() == null ? null : amount.amount().toString());
        return Map.copyOf(projection);
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private record ResolvedEvidence(
        com.adp.gateway.digitalasset.domain.TransactionDetailEvidence transaction,
        com.adp.gateway.digitalasset.domain.ReceiptFinalityEvidence receiptFinality,
        com.adp.gateway.digitalasset.domain.TransferExecutionEvidence transfer,
        com.adp.gateway.digitalasset.domain.ExactExecutionAmountEvidence exactAmount,
        String internalTraceDigest
    ) {
    }
}
