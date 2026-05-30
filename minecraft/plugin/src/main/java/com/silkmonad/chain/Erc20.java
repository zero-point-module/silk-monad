package com.silkmonad.chain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class Erc20 {

    /** keccak256("balanceOf(address)").substring(0, 8) */
    private static final String BALANCE_OF_SELECTOR = "70a08231";

    private Erc20() {
    }

    public static String encodeBalanceOf(String address) {
        String hex = address.toLowerCase();
        if (hex.startsWith("0x")) hex = hex.substring(2);
        if (hex.length() != 40) {
            throw new IllegalArgumentException("Invalid address length: " + address);
        }
        StringBuilder padded = new StringBuilder(64);
        for (int i = 0; i < 64 - 40; i++) padded.append('0');
        padded.append(hex);
        return "0x" + BALANCE_OF_SELECTOR + padded;
    }

    public static BigInteger decodeUint256(String hex) {
        String h = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (h.isEmpty()) return BigInteger.ZERO;
        return new BigInteger(h, 16);
    }

    public static BigDecimal format(BigInteger raw, int decimals) {
        return new BigDecimal(raw).movePointLeft(decimals).setScale(4, RoundingMode.DOWN).stripTrailingZeros();
    }
}
