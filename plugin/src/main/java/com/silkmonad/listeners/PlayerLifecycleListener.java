package com.silkmonad.listeners;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.hologram.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerLifecycleListener implements Listener {

    private final SilkMonadPlugin plugin;
    private final HologramManager holograms;

    public PlayerLifecycleListener(SilkMonadPlugin plugin, HologramManager holograms) {
        this.plugin = plugin;
        this.holograms = holograms;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        holograms.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        holograms.detach(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Re-attach a tick later — respawn fires before the player teleports to the new location.
        Bukkit.getScheduler().runTask(plugin, () -> holograms.refresh(event.getPlayer()));
    }
}
