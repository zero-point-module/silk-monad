package com.silkmonad.chat;

import com.silkmonad.SilkMonadPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BubbleListener implements Listener {

    private final SilkMonadPlugin plugin;
    private final BubbleManager bubbles;

    public BubbleListener(SilkMonadPlugin plugin, BubbleManager bubbles) {
        this.plugin = plugin;
        this.bubbles = bubbles;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        Component message = event.message();
        // AsyncChatEvent is async — hop to main thread for entity spawn.
        Bukkit.getScheduler().runTask(plugin, () -> bubbles.onChat(speaker, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bubbles.onQuit(event.getPlayer().getUniqueId());
    }
}
