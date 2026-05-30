package com.silkmonad.chat;

import org.bukkit.entity.TextDisplay;

public final class Bubble {

    public final TextDisplay display;
    public final long createdAtMillis;

    public Bubble(TextDisplay display, long createdAtMillis) {
        this.display = display;
        this.createdAtMillis = createdAtMillis;
    }

    public void remove() {
        if (!display.isDead()) display.remove();
    }
}
