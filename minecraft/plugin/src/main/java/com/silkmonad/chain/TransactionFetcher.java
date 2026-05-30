package com.silkmonad.chain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Pulls ERC-20 Transfer events involving a given wallet across every tracked
 * token. Monad testnet caps eth_getLogs at a 100-block range per call, so we
 * step backward from the latest block in 100-block chunks until we've covered
 * {@link #MAX_LOOKBACK_BLOCKS} of history, all in parallel.
 */
public final class TransactionFetcher {

    /** keccak256("Transfer(address,address,uint256)") */
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    /** Monad-imposed limit; do not exceed without changing the chunk strategy. */
    private static final int MAX_BLOCK_RANGE = 100;
    /** How far back in history to scan. ~5000 blocks ≈ 80 min at 1s blocks. */
    private static final int MAX_LOOKBACK_BLOCKS = 5000;

    private final ChainClient chain;
    private final TokenRegistry tokens;

    public TransactionFetcher(ChainClient chain, TokenRegistry tokens) {
        this.chain = chain;
        this.tokens = tokens;
    }

    public CompletableFuture<List<TransferEvent>> fetchInvolving(String address) {
        String paddedAddress = padTopicAddress(address);
        String addressArrayJson = tokenAddressArrayJson();
        Map<String, Token> byAddress = tokenAddressMap();

        return chain.ethBlockNumber().thenCompose(latest -> {
            BigInteger start = latest.subtract(BigInteger.valueOf(MAX_LOOKBACK_BLOCKS)).max(BigInteger.ZERO);
            List<CompletableFuture<JsonArray>> chunks = new ArrayList<>();
            for (BigInteger from = start; from.compareTo(latest) <= 0;
                 from = from.add(BigInteger.valueOf(MAX_BLOCK_RANGE))) {
                BigInteger to = from.add(BigInteger.valueOf(MAX_BLOCK_RANGE - 1L)).min(latest);
                String fromHex = "0x" + from.toString(16);
                String toHex = "0x" + to.toString(16);
                // Outgoing (from = address)
                chunks.add(chain.ethGetLogs(String.format(
                        "{\"fromBlock\":\"%s\",\"toBlock\":\"%s\",\"address\":%s,\"topics\":[\"%s\",\"%s\",null]}",
                        fromHex, toHex, addressArrayJson, TRANSFER_TOPIC, paddedAddress))
                        .exceptionally(ex -> new JsonArray()));
                // Incoming (to = address)
                chunks.add(chain.ethGetLogs(String.format(
                        "{\"fromBlock\":\"%s\",\"toBlock\":\"%s\",\"address\":%s,\"topics\":[\"%s\",null,\"%s\"]}",
                        fromHex, toHex, addressArrayJson, TRANSFER_TOPIC, paddedAddress))
                        .exceptionally(ex -> new JsonArray()));
            }
            return CompletableFuture.allOf(chunks.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<TransferEvent> events = new ArrayList<>();
                        for (CompletableFuture<JsonArray> f : chunks) {
                            decode(f.join(), byAddress, events);
                        }
                        events.sort(Comparator.comparing(TransferEvent::blockNumber).reversed());
                        return events;
                    });
        });
    }

    private void decode(JsonArray logs, Map<String, Token> byAddress, List<TransferEvent> out) {
        for (JsonElement e : logs) {
            JsonObject log = e.getAsJsonObject();
            String tokenAddress = log.get("address").getAsString().toLowerCase(Locale.ROOT);
            Token token = byAddress.get(tokenAddress);
            if (token == null) continue;
            JsonArray topics = log.getAsJsonArray("topics");
            if (topics.size() < 3) continue;
            String from = topicToAddress(topics.get(1).getAsString());
            String to = topicToAddress(topics.get(2).getAsString());
            String dataHex = log.get("data").getAsString();
            BigInteger amount = parseHex(dataHex);
            BigInteger blockNumber = parseHex(log.get("blockNumber").getAsString());
            String txHash = log.get("transactionHash").getAsString();
            out.add(new TransferEvent(token, from, to, amount, blockNumber, txHash));
        }
    }

    private String tokenAddressArrayJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Token t : tokens.all()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(t.address()).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, Token> tokenAddressMap() {
        Map<String, Token> map = new HashMap<>();
        for (Token t : tokens.all()) map.put(t.address().toLowerCase(Locale.ROOT), t);
        return map;
    }

    private static String padTopicAddress(String address) {
        String h = address.startsWith("0x") ? address.substring(2) : address;
        h = h.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("0x");
        for (int i = 0; i < 64 - h.length(); i++) sb.append('0');
        sb.append(h);
        return sb.toString();
    }

    private static String topicToAddress(String topic) {
        String h = topic.startsWith("0x") ? topic.substring(2) : topic;
        return "0x" + h.substring(h.length() - 40).toLowerCase(Locale.ROOT);
    }

    private static BigInteger parseHex(String hex) {
        String h = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (h.isEmpty()) return BigInteger.ZERO;
        return new BigInteger(h, 16);
    }
}
