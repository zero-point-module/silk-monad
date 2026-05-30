package com.silkmonad.merchant;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MerchantRegistry {

    /** keyed by lowercased wallet address */
    private final Map<String, Merchant> byAddress = new HashMap<>();

    public MerchantRegistry(JavaPlugin plugin) {
        plugin.saveResource("merchants.yml", true);
        File file = new File(plugin.getDataFolder(), "merchants.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        var list = cfg.getList("merchants");
        if (list == null) return;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) continue;
            String name = String.valueOf(m.get("name"));
            String address = String.valueOf(m.get("address"));
            TextColor color = null;
            if (m.get("color") != null) {
                String hex = String.valueOf(m.get("color"));
                color = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
            }
            byAddress.put(address.toLowerCase(Locale.ROOT), new Merchant(name, address, color));
        }
        plugin.getLogger().info("Loaded " + byAddress.size() + " merchant identity(ies).");
    }

    @Nullable
    public Merchant byAddress(@Nullable String address) {
        if (address == null) return null;
        return byAddress.get(address.toLowerCase(Locale.ROOT));
    }
}
