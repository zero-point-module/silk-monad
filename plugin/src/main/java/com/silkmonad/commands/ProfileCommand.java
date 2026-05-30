package com.silkmonad.commands;

import com.silkmonad.profile.PlayerProfileStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public final class ProfileCommand {

    private final PlayerProfileStore profiles;

    public ProfileCommand(PlayerProfileStore profiles) {
        this.profiles = profiles;
    }

    /** Handles `/silk color set [player] <#hex|named>` and `/silk color clear [player]`. */
    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /silk color <set|clear> ...", NamedTextColor.GRAY));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender, args);
            default -> sender.sendMessage(Component.text("Unknown color action: " + action, NamedTextColor.RED));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /silk color set <color>             — self
        // /silk color set <player> <color>    — others (op)
        UUID target;
        String colorArg;
        if (args.length == 2) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
                return;
            }
            if (!sender.hasPermission("silkmonad.profile.self")) {
                sender.sendMessage(Component.text("Missing permission silkmonad.profile.self", NamedTextColor.RED));
                return;
            }
            target = p.getUniqueId();
            colorArg = args[1];
        } else if (args.length == 3) {
            if (!sender.hasPermission("silkmonad.profile.others")) {
                sender.sendMessage(Component.text("Missing permission silkmonad.profile.others", NamedTextColor.RED));
                return;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) {
                sender.sendMessage(Component.text("No cached player named: " + args[1], NamedTextColor.RED));
                return;
            }
            target = op.getUniqueId();
            colorArg = args[2];
        } else {
            sender.sendMessage(Component.text("Usage: /silk color set <#hex|named>  or  /silk color set <player> <#hex|named>", NamedTextColor.GRAY));
            return;
        }
        TextColor color = parseColor(colorArg);
        if (color == null) {
            sender.sendMessage(Component.text("Invalid color: " + colorArg + " (use #rrggbb or a name like 'red')", NamedTextColor.RED));
            return;
        }
        profiles.update(target, p -> p.withChatColor(color));
        sender.sendMessage(Component.text("Set chat color to ", NamedTextColor.GREEN)
                .append(Component.text(color.asHexString(), color)));
    }

    private void handleClear(CommandSender sender, String[] args) {
        UUID target;
        if (args.length > 1) {
            if (!sender.hasPermission("silkmonad.profile.others")) {
                sender.sendMessage(Component.text("Missing permission silkmonad.profile.others", NamedTextColor.RED));
                return;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) {
                sender.sendMessage(Component.text("No cached player named: " + args[1], NamedTextColor.RED));
                return;
            }
            target = op.getUniqueId();
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return;
        }
        profiles.update(target, p -> p.withChatColor(null));
        sender.sendMessage(Component.text("Cleared chat color (now deterministic from UUID).", NamedTextColor.GREEN));
    }

    private static TextColor parseColor(String raw) {
        if (raw.startsWith("#")) return TextColor.fromHexString(raw);
        NamedTextColor named = NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
        return named;
    }
}
