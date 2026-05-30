package com.silkmonad.chat;

import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.UUID;

public final class ChatColorPalette {

    private static final List<TextColor> COLORS = List.of(
            TextColor.color(0xff8a4c), // warm orange
            TextColor.color(0xc084fc), // soft purple
            TextColor.color(0x4ade80), // jade green
            TextColor.color(0x60a5fa), // sky blue
            TextColor.color(0xfacc15), // gold
            TextColor.color(0xf472b6), // pink
            TextColor.color(0x34d399), // teal
            TextColor.color(0xfb7185), // coral
            TextColor.color(0xa78bfa), // lavender
            TextColor.color(0x22d3ee), // cyan
            TextColor.color(0xfb923c), // pumpkin
            TextColor.color(0xe879f9), // magenta
            TextColor.color(0x14b8a6), // emerald
            TextColor.color(0xeab308), // mustard
            TextColor.color(0x6ee7b7), // mint
            TextColor.color(0xfde047)  // lemon
    );

    private ChatColorPalette() {
    }

    public static TextColor forUuid(UUID uuid) {
        // UUID.hashCode() is a deterministic XOR of the bit halves — stable across JVMs.
        int idx = Math.floorMod(uuid.hashCode(), COLORS.size());
        return COLORS.get(idx);
    }
}
