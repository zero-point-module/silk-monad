package com.silkmonad.chain;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal JSON-RPC client for an EVM chain. We send a tiny set of calls
 * (eth_call, eth_getTransactionCount, eth_gasPrice, eth_sendRawTransaction)
 * and parse results by substring — keeping a JSON library out of the dep graph.
 */
public final class ChainClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String rpcUrl;
    private final AtomicLong rpcId = new AtomicLong(1);

    public ChainClient(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    public CompletableFuture<String> ethCall(String to, String data) {
        String params = String.format("[{\"to\":\"%s\",\"data\":\"%s\"},\"latest\"]", to, data);
        return rpc("eth_call", params);
    }

    public CompletableFuture<BigInteger> ethGetTransactionCount(String address) {
        String params = String.format("[\"%s\",\"pending\"]", address);
        return rpc("eth_getTransactionCount", params).thenApply(ChainClient::parseHexBig);
    }

    public CompletableFuture<BigInteger> ethGasPrice() {
        return rpc("eth_gasPrice", "[]").thenApply(ChainClient::parseHexBig);
    }

    public CompletableFuture<String> ethSendRawTransaction(String signedTxHex) {
        String params = String.format("[\"%s\"]", signedTxHex);
        return rpc("eth_sendRawTransaction", params);
    }

    private CompletableFuture<String> rpc(String method, String paramsJson) {
        long id = rpcId.getAndIncrement();
        String body = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}",
                id, method, paramsJson);
        HttpRequest req = HttpRequest.newBuilder(URI.create(rpcUrl))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ChainClient::extractResultOrThrow);
    }

    private static String extractResultOrThrow(String json) {
        int errIdx = json.indexOf("\"error\"");
        if (errIdx >= 0) throw new RuntimeException("RPC error: " + json);
        int idx = json.indexOf("\"result\"");
        if (idx < 0) throw new RuntimeException("No result in RPC response: " + json);
        int quote1 = json.indexOf('"', idx + 8);
        int quote2 = json.indexOf('"', quote1 + 1);
        if (quote1 < 0 || quote2 < 0) throw new RuntimeException("Malformed RPC response: " + json);
        return json.substring(quote1 + 1, quote2);
    }

    private static BigInteger parseHexBig(String hex) {
        String h = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (h.isEmpty()) return BigInteger.ZERO;
        return new BigInteger(h, 16);
    }
}
