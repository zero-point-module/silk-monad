package com.silkmonad.profile;

import org.jetbrains.annotations.Nullable;

public record PlayerProfile(@Nullable String wallet) {

    public static PlayerProfile empty() {
        return new PlayerProfile(null);
    }

    public PlayerProfile withWallet(@Nullable String wallet) {
        return new PlayerProfile(wallet);
    }
}
