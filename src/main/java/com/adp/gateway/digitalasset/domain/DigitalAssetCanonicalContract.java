package com.adp.gateway.digitalasset.domain;

import java.util.List;

import com.adp.gateway.common.error.ReasonCode;

public final class DigitalAssetCanonicalContract {
    public static final String SCHEMA_VERSION = "adp-digital-asset-runtime-contract/v1";
    public static final String ARTIFACT_ID = "ADP-DIGITAL-ASSET-RUNTIME-CONTRACT";
    public static final String ARTIFACT_VERSION = "1.0.0";
    public static final String ARTIFACT_CONTENT_DIGEST =
        "sha256:886842702fad124a95e73f0f714ffacef56d5cbd5a4c0f222440279d106b3504";
    public static final String CANONICALIZATION_VERSION = "adp-canonical-json/v1";

    public static final String EXECUTION_PACK = "DIGITAL_ASSET";
    public static final String BASELINE_WORKLOAD_ID = "tokenized_asset_purchase";
    public static final String BASELINE_PURPOSE_CODE = "DIGITAL_ASSET_PURCHASE";
    public static final String BASELINE_DESTINATION_PROFILE_ID = "dest_mock_asset_platform_v1";
    public static final String BASELINE_DESTINATION_PROFILE_VERSION = "1.0.0";
    public static final String BASELINE_DESTINATION_CONTRACT_VERSION = "digital-asset-egress-contract/v1";
    public static final String BASELINE_PROVIDER_REQUEST_SCHEMA_VERSION = "digital-asset-request/v1";
    public static final String EXTERNAL_RESULT_SCHEMA_VERSION = "digital-asset-external-result/v1";

    public static final List<ReasonCode> ACTIVE_REASON_CODES = List.of(
        ReasonCode.DIGITAL_ASSET_APPROVED_TRANSACTION_NOT_FOUND,
        ReasonCode.DIGITAL_ASSET_APPROVED_ASSET_MISMATCH,
        ReasonCode.DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED,
        ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH,
        ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_PROFILE_MISMATCH,
        ReasonCode.DIGITAL_ASSET_APPROVED_BENEFICIARY_MISMATCH,
        ReasonCode.DIGITAL_ASSET_APPROVED_PERIOD_VIOLATION,
        ReasonCode.DIGITAL_ASSET_REQUIRED_OUTBOUND_FIELD_MISSING,
        ReasonCode.DIGITAL_ASSET_REQUIRED_EXACT_VIOLATION,
        ReasonCode.DIGITAL_ASSET_TRANSFORM_NOT_ALLOWED,
        ReasonCode.DIGITAL_ASSET_DESTINATION_MAPPING_UNRESOLVED,
        ReasonCode.DIGITAL_ASSET_TRACE_BINDING_INVALID,
        ReasonCode.DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID,
        ReasonCode.DIGITAL_ASSET_RUNTIME_DATA_CLASS_UNMAPPED,
        ReasonCode.DIGITAL_ASSET_CONTRACT_GAP
    );

    private DigitalAssetCanonicalContract() {
    }
}
