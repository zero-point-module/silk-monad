package com.silkmonad.chat;

import org.bukkit.entity.TextDisplay;

public final class Bubble {

    public final TextDisplay display;
    public final long createdAtMillis;
    /** Estimated number of rendered lines (including wrap). Used for stacking math. */
    public final int lineCount;

    public Bubble(TextDisplay display, long createdAtMillis, int lineCount) {
        this.display = display;
        this.createdAtMillis = createdAtMillis;
        this.lineCount = lineCount;
    }

    public void remove() {
        if (!display.isDead()) display.remove();
    }
}
