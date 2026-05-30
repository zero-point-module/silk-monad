package com.silkmonad.profile;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class PlayerProfileStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public PlayerProfileStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public PlayerProfile get(UUID uuid) {
        return profiles.getOrDefault(uuid, PlayerProfile.empty());
    }

    public void update(UUID uuid, UnaryOperator<PlayerProfile> fn) {
        PlayerProfile updated = fn.apply(get(uuid));
        profiles.put(uuid, updated);
        save();
    }

    public void load() {
        profiles.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            String wallet = s.getString("wallet");
            TextColor color = parseColor(s.getString("chat-color"));
            boolean bold = s.getBoolean("chat-bold", true);
            profiles.put(uuid, new PlayerProfile(wallet, color, bold));
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerProfile> e : profiles.entrySet()) {
            PlayerProfile p = e.getValue();
            String base = "players." + e.getKey();
            if (p.wallet() != null) cfg.set(base + ".wallet", p.wallet());
            if (p.chatColor() != null) cfg.set(base + ".chat-color", p.chatColor().asHexString());
            cfg.set(base + ".chat-bold", p.chatBold());
        }
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save players.yml: " + ex.getMessage());
        }
    }

    @Nullable
    private static TextColor parseColor(@Nullable String raw) {
        if (raw == null) return null;
        return TextColor.fromHexString(raw.startsWith("#") ? raw : "#" + raw);
    }
}
