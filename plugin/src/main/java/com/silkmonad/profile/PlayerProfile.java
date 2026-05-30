package com.silkmonad.profile;

import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

public record PlayerProfile(
        @Nullable String wallet,
        @Nullable TextColor chatColor,
        boolean chatBold
) {

    public static PlayerProfile empty() {
        return new PlayerProfile(null, null, true);
    }

    public PlayerProfile withWallet(@Nullable String wallet) {
        return new PlayerProfile(wallet, chatColor, chatBold);
    }

    public PlayerProfile withChatColor(@Nullable TextColor chatColor) {
        return new PlayerProfile(wallet, chatColor, chatBold);
    }
}
