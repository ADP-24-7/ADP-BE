package com.adp.gateway.digitalasset.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record DigitalAssetPurchaseInput(
    String customerId,
    String accountId,
    String walletAddress,
    String assetId,
    BigDecimal amount,
    String kycStatus,
    String amlStatus,
    boolean walletVerified
) {
    private static final Set<String> KEYS = Set.of(
        "customerId", "accountId", "walletAddress", "assetId", "amount",
        "kycStatus", "amlStatus", "walletVerified"
    );

    public static DigitalAssetPurchaseInput from(Map<String, Object> input) {
        if (input == null || !input.keySet().equals(KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_SCHEMA_MISMATCH");
        }
        String customerId = text(input.get("customerId"));
        String accountId = text(input.get("accountId"));
        String walletAddress = text(input.get("walletAddress"));
        String assetId = text(input.get("assetId"));
        String kycStatus = text(input.get("kycStatus"));
        String amlStatus = text(input.get("amlStatus"));
        if (!(input.get("walletVerified") instanceof Boolean walletVerified)
            || !(input.get("amount") instanceof Number number)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_INVALID");
        }
        BigDecimal amount = new BigDecimal(number.toString()).stripTrailingZeros();
        if (amount.signum() <= 0 || amount.precision() > 18 || Math.max(amount.scale(), 0) > 8) {
            throw new IllegalArgumentException("DIGITAL_ASSET_AMOUNT_INVALID");
        }
        return new DigitalAssetPurchaseInput(
            customerId, accountId, walletAddress, assetId, amount, kycStatus, amlStatus, walletVerified
        );
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 240) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_INVALID");
        }
        return text;
    }
}
