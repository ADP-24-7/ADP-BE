package com.adp.gateway.digitalasset.domain;

import java.math.BigDecimal;
import java.math.BigInteger;

public record DigitalAssetAmount(BigInteger atomicUnits) implements Comparable<DigitalAssetAmount> {

    public DigitalAssetAmount {
        if (atomicUnits == null || atomicUnits.signum() < 0 || atomicUnits.toString().length() > 78) {
            throw new IllegalArgumentException("DIGITAL_ASSET_AMOUNT_INVALID");
        }
    }

    public static DigitalAssetAmount from(Object value) {
        try {
            if (value instanceof BigInteger integer) {
                return new DigitalAssetAmount(integer);
            }
            if (value instanceof Number number) {
                BigDecimal decimal = new BigDecimal(number.toString());
                return new DigitalAssetAmount(decimal.toBigIntegerExact());
            }
            if (value instanceof String text && text.matches("[0-9]+")) {
                return new DigitalAssetAmount(new BigInteger(text));
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("DIGITAL_ASSET_AMOUNT_INVALID", exception);
        }
        throw new IllegalArgumentException("DIGITAL_ASSET_AMOUNT_INVALID");
    }

    @Override
    public int compareTo(DigitalAssetAmount other) {
        return atomicUnits.compareTo(other.atomicUnits);
    }

    @Override
    public String toString() {
        return atomicUnits.toString();
    }
}
