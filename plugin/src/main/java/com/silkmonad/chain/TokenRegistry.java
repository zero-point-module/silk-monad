package com.silkmonad.chain;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TokenRegistry {

    private final List<Token> tokens = new ArrayList<>();

    public TokenRegistry(JavaPlugin plugin) {
        plugin.saveResource("tokens.yml", true);
        File file = new File(plugin.getDataFolder(), "tokens.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("tokens");
        if (list == null) {
            plugin.getLogger().warning("tokens.yml is empty or missing 'tokens' key");
            return;
        }
        for (Object entry : list) {
            if (!(entry instanceof java.util.Map<?, ?> m)) continue;
            String symbol = String.valueOf(m.get("symbol"));
            String address = String.valueOf(m.get("address"));
            int decimals = m.get("decimals") instanceof Number n ? n.intValue() : 18;
            String glyph = m.get("glyph") == null ? null : String.valueOf(m.get("glyph"));
            TextColor color = null;
            if (m.get("color") != null) {
                String hex = String.valueOf(m.get("color"));
                color = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
            }
            tokens.add(new Token(symbol, address, decimals, glyph, color));
        }
        plugin.getLogger().info("Tracking " + tokens.size() + " ERC20 token(s).");
    }

    public List<Token> all() {
        return Collections.unmodifiableList(tokens);
    }
}
