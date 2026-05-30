package com.silkmonad.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UuidCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /uuid <player>", NamedTextColor.GRAY));
            return true;
        }

        String name = args[0];
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(name);
        if (player == null) {
            sender.sendMessage(Component.text("No cached player named '" + name + "'. They must have connected at least once.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text(player.getName(), NamedTextColor.GREEN)
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(player.getUniqueId().toString(), NamedTextColor.YELLOW)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String n = op.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(n);
            }
        }
        return out;
    }
}
