package com.silkmonad.chain;

import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

public record Token(
        String symbol,
        String address,
        int decimals,
        @Nullable String glyph,
        @Nullable TextColor color
) {
}
