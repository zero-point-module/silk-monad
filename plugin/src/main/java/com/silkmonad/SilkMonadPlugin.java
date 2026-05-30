package com.silkmonad;

import com.silkmonad.chain.BalanceFetcher;
import com.silkmonad.chain.ChainClient;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.chain.Treasury;
import com.silkmonad.chat.ChatFormatter;
import com.silkmonad.commands.ProfileCommand;
import com.silkmonad.commands.SilkCommand;
import com.silkmonad.commands.UuidCommand;
import com.silkmonad.commands.WalletCommand;
import com.silkmonad.cosmetic.CosmeticRegistry;
import com.silkmonad.cosmetic.item.ItemCosmeticLoader;
import com.silkmonad.hologram.HologramManager;
import com.silkmonad.listeners.PlayerLifecycleListener;
import com.silkmonad.merchant.MerchantRegistry;
import com.silkmonad.profile.PlayerProfileStore;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class SilkMonadPlugin extends JavaPlugin {

    private static SilkMonadPlugin instance;
    private CosmeticRegistry registry;
    private PlayerProfileStore profiles;
    private HologramManager holograms;
    private TokenRegistry tokens;
    private Treasury treasury;
    private WalletCommand walletCommand;
    private ProfileCommand profileCommand;

    public static SilkMonadPlugin get() {
        return instance;
    }

    public CosmeticRegistry registry() {
        return registry;
    }

    public PlayerProfileStore profiles() {
        return profiles;
    }

    public HologramManager holograms() {
        return holograms;
    }

    public WalletCommand walletCommand() {
        return walletCommand;
    }

    public ProfileCommand profileCommand() {
        return profileCommand;
    }

    public TokenRegistry tokens() {
        return tokens;
    }

    public Treasury treasury() {
        return treasury;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Cosmetics
        this.registry = new CosmeticRegistry();
        reloadCosmetics();

        // Chain + holograms + treasury
        String rpcUrl = getConfig().getString("chain.rpc-url", "https://testnet-rpc.monad.xyz");
        long chainId = getConfig().getLong("chain.chain-id", 10143L);
        ChainClient chain = new ChainClient(rpcUrl);
        this.tokens = new TokenRegistry(this);
        BalanceFetcher balanceFetcher = new BalanceFetcher(chain, tokens);
        this.treasury = new Treasury(this, chain, chainId);
        this.profiles = new PlayerProfileStore(this);
        MerchantRegistry merchants = new MerchantRegistry(this);
        this.holograms = new HologramManager(this, balanceFetcher, tokens, merchants, profiles);

        // Commands
        this.walletCommand = new WalletCommand(this, profiles, holograms);
        this.profileCommand = new ProfileCommand(profiles);
        getCommand("silk").setExecutor(new SilkCommand(this));
        UuidCommand uuid = new UuidCommand();
        getCommand("uuid").setExecutor(uuid);
        getCommand("uuid").setTabCompleter(uuid);

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, holograms), this);
        getServer().getPluginManager().registerEvents(new ChatFormatter(profiles), this);

        getLogger().info("Loaded " + registry.size() + " cosmetic(s).");
    }

    @Override
    public void onDisable() {
        if (holograms != null) holograms.detachAll();
    }

    /**
     * Extract every bundled cosmetic YAML to the plugin data folder, overwriting any
     * existing file. Bundled cosmetics are the source of truth; user-added YAMLs (those
     * not present in the jar) are preserved. To customize a bundled cosmetic, edit it
     * in plugin/src/main/resources/cosmetics/ and rebuild.
     */
    private void saveBundledCosmetics() {
        try (JarFile jar = new JarFile(new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))) {
            jar.stream()
                    .map(JarEntry::getName)
                    .filter(n -> n.startsWith("cosmetics/") && n.endsWith(".yml"))
                    .forEach(n -> saveResource(n, true));
        } catch (IOException | URISyntaxException e) {
            getLogger().warning("Could not extract bundled cosmetics: " + e.getMessage());
        }
    }

    public void reloadCosmetics() {
        saveBundledCosmetics();
        registry.clear();
        ItemCosmeticLoader.loadAll(this, getDataFolder().toPath().resolve("cosmetics"), registry);
    }
}
