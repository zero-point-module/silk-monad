package com.silkmonad.commands;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.hologram.HologramManager;
import com.silkmonad.profile.PlayerProfile;
import com.silkmonad.profile.PlayerProfileStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WalletCommand {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final SilkMonadPlugin plugin;
    private final PlayerProfileStore profiles;
    private final HologramManager holograms;

    public WalletCommand(SilkMonadPlugin plugin, PlayerProfileStore profiles, HologramManager holograms) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.holograms = holograms;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /silk wallet <set|clear|show|refresh> ...", NamedTextColor.GRAY));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender, args);
            case "show" -> handleShow(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            default -> sender.sendMessage(Component.text("Unknown wallet action: " + action, NamedTextColor.RED));
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        // /silk wallet set <0x...>                — self
        // /silk wallet set <player> <0x...>       — others (op)
        UUID target;
        String address;
        String displayName;
        if (args.length == 2) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
                return;
            }
            if (!sender.hasPermission("silkmonad.wallet.self")) {
                sender.sendMessage(Component.text("Missing permission silkmonad.wallet.self", NamedTextColor.RED));
                return;
            }
            target = p.getUniqueId();
            displayName = p.getName();
            address = args[1];
        } else if (args.length == 3) {
            if (!sender.hasPermission("silkmonad.wallet.others")) {
                sender.sendMessage(Component.text("Missing permission silkmonad.wallet.others", NamedTextColor.RED));
                return;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) {
                sender.sendMessage(Component.text("No cached player named: " + args[1], NamedTextColor.RED));
                return;
            }
            target = op.getUniqueId();
            displayName = op.getName();
            address = args[2];
        } else {
            sender.sendMessage(Component.text("Usage: /silk wallet set <0x...>  or  /silk wallet set <player> <0x...>", NamedTextColor.GRAY));
            return;
        }
        if (!ADDRESS.matcher(address).matches()) {
            sender.sendMessage(Component.text("Invalid address. Expected 0x followed by 40 hex chars.", NamedTextColor.RED));
            return;
        }
        profiles.update(target, p -> p.withWallet(address.toLowerCase(Locale.ROOT)));
        sender.sendMessage(Component.text("Linked " + displayName + " -> " + address, NamedTextColor.GREEN));
        Player online = Bukkit.getPlayer(target);
        if (online != null) holograms.refresh(online);
    }

    private void handleClear(CommandSender sender, String[] args) {
        UUID target = resolveTarget(sender, args, 1, "silkmonad.wallet.others");
        if (target == null) return;
        profiles.update(target, p -> p.withWallet(null));
        sender.sendMessage(Component.text("Cleared wallet.", NamedTextColor.GREEN));
        holograms.detach(target);
    }

    private void handleShow(CommandSender sender, String[] args) {
        UUID target = resolveTarget(sender, args, 1, "silkmonad.wallet.show");
        if (target == null) return;
        PlayerProfile profile = profiles.get(target);
        if (profile.wallet() == null) {
            sender.sendMessage(Component.text("No wallet linked.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Wallet: ", NamedTextColor.GRAY)
                    .append(Component.text(profile.wallet(), NamedTextColor.YELLOW)));
        }
    }

    private void handleRefresh(CommandSender sender, String[] args) {
        UUID target = resolveTarget(sender, args, 1, "silkmonad.wallet.others");
        if (target == null) return;
        Player p = Bukkit.getPlayer(target);
        if (p == null) {
            sender.sendMessage(Component.text("Player must be online to refresh.", NamedTextColor.RED));
            return;
        }
        holograms.refresh(p);
        sender.sendMessage(Component.text("Refreshing balances...", NamedTextColor.GREEN));
    }

    /**
     * For commands that take an optional [player] argument. Self if absent, requires
     * `othersPermission` if specified.
     */
    private UUID resolveTarget(CommandSender sender, String[] args, int index, String othersPermission) {
        if (args.length > index) {
            if (!sender.hasPermission(othersPermission)) {
                sender.sendMessage(Component.text("Missing permission " + othersPermission, NamedTextColor.RED));
                return null;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[index]);
            if (op == null) {
                sender.sendMessage(Component.text("No cached player named: " + args[index], NamedTextColor.RED));
                return null;
            }
            return op.getUniqueId();
        }
        if (sender instanceof Player p) return p.getUniqueId();
        sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
        return null;
    }
}
