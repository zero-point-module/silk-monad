package com.silkmonad;

import com.silkmonad.commands.SilkCommand;
import com.silkmonad.cosmetic.CosmeticRegistry;
import com.silkmonad.cosmetic.item.ItemCosmeticLoader;
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

    public static SilkMonadPlugin get() {
        return instance;
    }

    public CosmeticRegistry registry() {
        return registry;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    @Override
    public void onEnable() {
        instance = this;
        this.registry = new CosmeticRegistry();
        reloadCosmetics();

        getCommand("silk").setExecutor(new SilkCommand(this));
        getLogger().info("Loaded " + registry.size() + " cosmetic(s).");
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
