package com.silkmonad.cosmetic.item;

import com.silkmonad.cosmetic.CosmeticRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ItemCosmeticLoader {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ItemCosmeticLoader() {
    }

    public static void loadAll(JavaPlugin plugin, Path directory, CosmeticRegistry registry) {
        if (!Files.isDirectory(directory)) {
            plugin.getLogger().warning("Cosmetics directory missing: " + directory);
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(".yml"))
                 .forEach(p -> loadOne(plugin, p, registry));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to list cosmetics directory: " + e.getMessage());
        }
    }

    private static void loadOne(JavaPlugin plugin, Path file, CosmeticRegistry registry) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file.toFile());
        String id = cfg.getString("id");
        String type = cfg.getString("type", "item");
        if (id == null || !"item".equalsIgnoreCase(type)) {
            return;
        }

        Component displayName = MM.deserialize(cfg.getString("display-name", id));
        ConfigurationSection itemSection = cfg.getConfigurationSection("item");
        if (itemSection == null) {
            plugin.getLogger().warning("Cosmetic " + id + " missing 'item' section: " + file);
            return;
        }

        Material material = Material.matchMaterial(itemSection.getString("material", "STONE").toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("Unknown material for cosmetic " + id);
            return;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            List<String> loreLines = itemSection.getStringList("lore");
            if (!loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(MM::deserialize).toList());
            }
            if (itemSection.contains("custom-model-data")) {
                meta.setCustomModelData(itemSection.getInt("custom-model-data"));
            }
            String itemModel = itemSection.getString("item-model");
            if (itemModel != null && !itemModel.isBlank()) {
                NamespacedKey modelKey = NamespacedKey.fromString(itemModel);
                if (modelKey != null) {
                    meta.setItemModel(modelKey);
                } else {
                    plugin.getLogger().warning("Cosmetic " + id + " has invalid item-model: " + itemModel);
                }
            }
            if (itemSection.getBoolean("unbreakable", false)) {
                meta.setUnbreakable(true);
            }
            if (itemSection.getBoolean("hide-attributes", false)) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            ConfigurationSection ench = itemSection.getConfigurationSection("enchantments");
            if (ench != null) {
                for (String key : ench.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, ench.getInt(key), true);
                    }
                }
            }
            stack.setItemMeta(meta);
        }

        registry.register(new ItemCosmetic(id, displayName, stack));
    }
}
