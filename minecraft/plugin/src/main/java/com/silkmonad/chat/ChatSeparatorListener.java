package com.silkmonad.chat;

import com.silkmonad.SilkMonadPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * After every chat message is processed, broadcast a thin decorative bar
 * to all players so messages don't visually run together in the chat log.
 */
public final class ChatSeparatorListener implements Listener {

    private static final Component SEPARATOR = Component.text(
            "━".repeat(40), NamedTextColor.DARK_GRAY);

    private final SilkMonadPlugin plugin;

    public ChatSeparatorListener(SilkMonadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // AsyncChatEvent runs async and the message hasn't actually been delivered
        // to viewers yet. Hop to the main thread next tick so the separator lands
        // after the message in everyone's chat log.
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(SEPARATOR));
    }
}
