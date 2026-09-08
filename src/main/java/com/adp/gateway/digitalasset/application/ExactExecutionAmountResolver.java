package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.ExactExecutionAmountEvidence;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.DigitalAssetEvidenceSourceType;
import com.adp.gateway.digitalasset.domain.TransactionDetailEvidence;
import com.adp.gateway.digitalasset.domain.TransferExecutionEvidence;

public interface ExactExecutionAmountResolver {
    ExactExecutionAmountEvidence resolveAmount(
        ExternalExecutionResult result,
        TransactionDetailEvidence transaction,
        TransferExecutionEvidence transfer
    );

    DigitalAssetEvidenceSourceType sourceType();
}
