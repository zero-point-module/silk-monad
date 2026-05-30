package com.silkmonad.chain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal JSON-RPC client for an EVM chain. One method we need: eth_call.
 * Responses are parsed lazily by the caller via simple substring extraction —
 * keeping a JSON library out of the dependency graph.
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
        long id = rpcId.getAndIncrement();
        String body = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"eth_call\",\"params\":[{\"to\":\"%s\",\"data\":\"%s\"},\"latest\"]}",
                id, to, data);
        HttpRequest req = HttpRequest.newBuilder(URI.create(rpcUrl))
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(ChainClient::extractResultOrThrow);
    }

    private static String extractResultOrThrow(String json) {
        // Look for "result":"0x..." — RPC always returns hex strings for eth_call.
        int errIdx = json.indexOf("\"error\"");
        if (errIdx >= 0) {
            throw new RuntimeException("RPC error: " + json);
        }
        int idx = json.indexOf("\"result\"");
        if (idx < 0) throw new RuntimeException("No result in RPC response: " + json);
        int quote1 = json.indexOf('"', idx + 8);
        int quote2 = json.indexOf('"', quote1 + 1);
        if (quote1 < 0 || quote2 < 0) throw new RuntimeException("Malformed RPC response: " + json);
        return json.substring(quote1 + 1, quote2);
    }
}
