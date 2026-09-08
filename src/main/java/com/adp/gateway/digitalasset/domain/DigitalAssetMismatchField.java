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
    CHAIN_ID("chainId", true),
    RECIPIENT_ADDRESS("recipientAddress", true),
    ASSET_KIND("assetKind", true),
    ASSET_SYMBOL("assetSymbol", true),
    ASSET_CONTRACT_ADDRESS("assetContractAddress", true),
    OPERATION("operation", true),
    TOKEN_ID("tokenId", true),
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
