package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.digitalasset.domain.DigitalAssetEvidenceSourceType;
import com.adp.gateway.digitalasset.domain.ReceiptFinalityEvidence;

public interface ReceiptFinalityResolver {
    ReceiptFinalityEvidence resolveReceiptFinality(ExternalExecutionResult result);

    DigitalAssetEvidenceSourceType sourceType();
}
