package com.silkmonad.chain;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.io.File;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps the deployer wallet — the single account that distributes the SPICE/SILK/JADE
 * supply on Monad testnet. /silk give (for a token cosmetic) calls {@link #transfer}
 * to move that token from the deployer to the player's linked wallet.
 *
 * The PK is loaded from data/plugins/SilkMonad/secrets.yml (gitignored) on enable.
 * If the file or key is missing, mints are skipped with a log warning — the in-game
 * cosmetic give still works.
 */
public final class Treasury {

    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(80_000L);
    /** Wei multiplier applied to the human-friendly gas price; bump if Monad raises minimum tip. */
    private static final BigInteger GAS_PRICE_FLOOR = BigInteger.valueOf(50_000_000_000L); // 50 gwei

    private final JavaPlugin plugin;
    private final ChainClient chain;
    private final long chainId;
    @Nullable
    private final Credentials credentials;

    public Treasury(JavaPlugin plugin, ChainClient chain, long chainId) {
        this.plugin = plugin;
        this.chain = chain;
        this.chainId = chainId;
        this.credentials = loadCredentials(plugin);
        if (credentials != null) {
            plugin.getLogger().info("Treasury wallet loaded: " + credentials.getAddress());
        } else {
            plugin.getLogger().warning("No deployer-private-key found; /silk give will not mint on-chain.");
        }
    }

    public boolean isReady() {
        return credentials != null;
    }

    @Nullable
    public String treasuryAddress() {
        return credentials == null ? null : credentials.getAddress();
    }

    /**
     * Sends `amount * 10^decimals` of the token from the deployer to `to`. Returns the
     * transaction hash on success.
     */
    public CompletableFuture<String> transfer(Token token, String to, BigInteger wholeAmount) {
        if (credentials == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Treasury not configured"));
        }
        BigInteger raw = wholeAmount.multiply(BigInteger.TEN.pow(token.decimals()));
        Function fn = new Function(
                "transfer",
                List.of(new Address(to), new Uint256(raw)),
                Collections.emptyList());
        String data = FunctionEncoder.encode(fn);

        CompletableFuture<BigInteger> nonceFuture = chain.ethGetTransactionCount(credentials.getAddress());
        CompletableFuture<BigInteger> gasFuture = chain.ethGasPrice().exceptionally(ex -> GAS_PRICE_FLOOR);

        return nonceFuture.thenCombine(gasFuture, (nonce, gasPrice) -> {
            BigInteger effectiveGas = gasPrice.max(GAS_PRICE_FLOOR);
            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, effectiveGas, GAS_LIMIT, token.address(), BigInteger.ZERO, data);
            byte[] signed = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            return Numeric.toHexString(signed);
        }).thenCompose(chain::ethSendRawTransaction);
    }

    @Nullable
    private static Credentials loadCredentials(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "secrets.yml");
        if (!f.exists()) {
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String pk = cfg.getString("deployer-private-key");
        if (pk == null || pk.isBlank()) {
            return null;
        }
        try {
            return Credentials.create(pk.trim());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse deployer-private-key in secrets.yml: " + e.getMessage());
            return null;
        }
    }
}
