package com.silkmonad.chain;

import java.math.BigInteger;

/**
 * One ERC-20 Transfer event decoded from an eth_getLogs response.
 *
 * @param token        the matching {@link Token} (symbol/decimals/etc.)
 * @param from         sender address (lowercase 0x...)
 * @param to           recipient address (lowercase 0x...)
 * @param rawAmount    raw amount (subject to token.decimals())
 * @param blockNumber  block where the transfer was mined
 * @param txHash       full 0x... transaction hash
 */
public record TransferEvent(
        Token token,
        String from,
        String to,
        BigInteger rawAmount,
        BigInteger blockNumber,
        String txHash
) {
}
