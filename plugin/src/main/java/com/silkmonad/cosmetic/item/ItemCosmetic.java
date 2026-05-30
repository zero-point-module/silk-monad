package com.silkmonad.cosmetic.item;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.cosmetic.Cosmetic;
import com.silkmonad.cosmetic.CosmeticType;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public final class ItemCosmetic implements Cosmetic {

    public static final String PDC_KEY = "cosmetic_id";

    private final String id;
    private final Component displayName;
    private final ItemStack template;

    public ItemCosmetic(String id, Component displayName, ItemStack template) {
        this.id = id;
        this.displayName = displayName;
        this.template = template;
        tag(template);
    }

    private void tag(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        NamespacedKey key = SilkMonadPlugin.get().key(PDC_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    public ItemStack newStack() {
        return template.clone();
    }

    public ItemStack newStack(int amount) {
        ItemStack s = template.clone();
        int max = s.getMaxStackSize();
        s.setAmount(Math.max(1, Math.min(amount, max)));
        return s;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull Component displayName() {
        return displayName;
    }

    @Override
    public @NotNull CosmeticType type() {
        return CosmeticType.ITEM;
    }

    @Override
    public void apply(@NotNull Player player) {
        player.getInventory().addItem(newStack());
    }

    @Override
    public void remove(@NotNull Player player) {
        NamespacedKey key = SilkMonadPlugin.get().key(PDC_KEY);
        player.getInventory().forEach(stack -> {
            if (stack == null) return;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return;
            String tagged = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (id.equals(tagged)) stack.setAmount(0);
        });
    }
}
