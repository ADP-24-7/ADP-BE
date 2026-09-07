package com.adp.gateway.digitalasset.domain;

public enum DigitalAssetMismatchField {
    CUSTOMER_TOKEN("customerToken", true),
    ACCOUNT_TOKEN("accountToken", true),
    WALLET_ADDRESS("walletAddress", true),
    ASSET_ID("assetId", true),
    AMOUNT("amount", true),
    KYC_STATUS("kycStatus", false),
    AML_STATUS("amlStatus", false),
    WALLET_VERIFIED("walletVerified", false),
    EXTERNAL_REQUEST_ID("externalRequestId", true),
    UNEXPECTED_FIELD(null, true);

    private final String externalName;
    private final boolean critical;

    DigitalAssetMismatchField(String externalName, boolean critical) {
        this.externalName = externalName;
        this.critical = critical;
    }

    public String externalName() {
        return externalName;
    }

    public boolean critical() {
        return critical;
    }
}
