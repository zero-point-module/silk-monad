package com.silkmonad.cosmetic;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface Cosmetic {

    @NotNull String id();

    @NotNull Component displayName();

    @NotNull CosmeticType type();

    void apply(@NotNull Player player);

    void remove(@NotNull Player player);
}
