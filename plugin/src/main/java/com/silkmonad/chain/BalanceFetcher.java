package com.silkmonad.chain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class BalanceFetcher {

    private final ChainClient chain;
    private final TokenRegistry registry;

    public BalanceFetcher(ChainClient chain, TokenRegistry registry) {
        this.chain = chain;
        this.registry = registry;
    }

    public CompletableFuture<Map<String, BigDecimal>> fetch(String walletAddress) {
        List<Token> tokens = registry.all();
        @SuppressWarnings("unchecked")
        CompletableFuture<BigDecimal>[] futures = new CompletableFuture[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String data = Erc20.encodeBalanceOf(walletAddress);
            futures[i] = chain.ethCall(t.address(), data)
                    .thenApply(Erc20::decodeUint256)
                    .thenApply(raw -> Erc20.format(raw, t.decimals()))
                    .exceptionally(ex -> {
                        // Network/RPC failure for this token — show zero rather than break the hologram.
                        return BigDecimal.ZERO;
                    });
        }
        return CompletableFuture.allOf(futures).thenApply(v -> {
            Map<String, BigDecimal> out = new LinkedHashMap<>();
            for (int i = 0; i < tokens.size(); i++) {
                out.put(tokens.get(i).symbol(), futures[i].join());
            }
            return out;
        });
    }
}
