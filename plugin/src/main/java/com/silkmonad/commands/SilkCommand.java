package com.silkmonad.commands;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.cosmetic.Cosmetic;
import com.silkmonad.cosmetic.CosmeticType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SilkCommand implements CommandExecutor, TabCompleter {

    private final SilkMonadPlugin plugin;

    public SilkCommand(SilkMonadPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /silk <give|apply|remove|list|reload>", NamedTextColor.GRAY));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadCosmetics();
                sender.sendMessage(Component.text("Reloaded " + plugin.registry().size() + " cosmetic(s).", NamedTextColor.GREEN));
            }
            case "list" -> {
                CosmeticType filter = args.length >= 2 ? parseType(args[1]) : null;
                sender.sendMessage(Component.text("Cosmetics" + (filter == null ? "" : " (" + filter + ")") + ":", NamedTextColor.GOLD));
                plugin.registry().all().stream()
                        .filter(c -> filter == null || c.type() == filter)
                        .forEach(c -> sender.sendMessage(
                                Component.text(" silk:" + c.id() + " ", NamedTextColor.YELLOW)
                                        .append(c.displayName())
                                        .append(Component.text(" [" + c.type() + "]", NamedTextColor.GRAY))));
            }
            case "give", "apply" -> handleApply(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
        }
        return true;
    }

    private void handleApply(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /silk " + args[0] + " <id> [player]", NamedTextColor.RED));
            return;
        }
        Optional<Cosmetic> cosmetic = plugin.registry().get(args[1]);
        if (cosmetic.isEmpty()) {
            sender.sendMessage(Component.text("No cosmetic with id: " + args[1], NamedTextColor.RED));
            return;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        cosmetic.get().apply(target);
        sender.sendMessage(Component.text("Applied ", NamedTextColor.GREEN)
                .append(cosmetic.get().displayName())
                .append(Component.text(" to " + target.getName(), NamedTextColor.GREEN)));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /silk remove <id> [player]", NamedTextColor.RED));
            return;
        }
        Optional<Cosmetic> cosmetic = plugin.registry().get(args[1]);
        if (cosmetic.isEmpty()) {
            sender.sendMessage(Component.text("No cosmetic with id: " + args[1], NamedTextColor.RED));
            return;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        cosmetic.get().remove(target);
        sender.sendMessage(Component.text("Removed " + cosmetic.get().id() + " from " + target.getName(), NamedTextColor.GREEN));
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) sender.sendMessage(Component.text("Player not online: " + args[index], NamedTextColor.RED));
            return p;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
        return null;
    }

    private CosmeticType parseType(String raw) {
        try {
            return CosmeticType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("give", "apply", "remove", "list", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("apply") || args[0].equalsIgnoreCase("remove"))) {
            return filter(plugin.registry().all().stream().map(Cosmetic::id).toList(), args[1]);
        }
        if (args.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> choices, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : choices) if (c.toLowerCase(Locale.ROOT).startsWith(p)) out.add(c);
        return out;
    }
}
