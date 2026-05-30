package com.silkmonad.commands;

import com.silkmonad.SilkMonadPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class CrowdCommand {

    private static final int DEFAULT_COUNT = 60;
    private static final int MAX_COUNT = 200;

    private final SilkMonadPlugin plugin;

    public CrowdCommand(SilkMonadPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("silkmonad.crowd")) {
            sender.sendMessage(Component.text("Missing permission silkmonad.crowd", NamedTextColor.RED));
            return;
        }
        if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("clear")) {
            int before = plugin.crowdManager().activeCount();
            plugin.crowdManager().clear();
            sender.sendMessage(Component.text("Cleared " + before + " villager(s).", NamedTextColor.GREEN));
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Run from in-game so I know where to spawn the crowd.", NamedTextColor.RED));
            return;
        }
        int count = DEFAULT_COUNT;
        if (args.length >= 1) {
            try {
                count = Math.max(1, Math.min(MAX_COUNT, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Component.text(
                        "Usage: /silk crowd [count|clear]  (default " + DEFAULT_COUNT + ", max " + MAX_COUNT + ")",
                        NamedTextColor.GRAY));
                return;
            }
        }
        plugin.crowdManager().spawn(p.getLocation(), count);
        sender.sendMessage(Component.text("Spawned " + count + " villager(s). They'll wander and pair off.",
                NamedTextColor.GREEN));
    }
}
