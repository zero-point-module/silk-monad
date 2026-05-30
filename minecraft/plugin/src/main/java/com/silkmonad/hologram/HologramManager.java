package com.silkmonad.hologram;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.BalanceFetcher;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.merchant.Merchant;
import com.silkmonad.merchant.MerchantRegistry;
import com.silkmonad.profile.PlayerProfile;
import com.silkmonad.profile.PlayerProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Holograms for wallet-linked players. Each is a free-floating TextDisplay we
 * teleport every {@link #TICK_INTERVAL} ticks to sit at chest height on the
 * player's right-hand side. Display interpolation smooths the motion so it
 * doesn't feel like the text is chasing the player.
 */
public final class HologramManager {

    private static final long TICK_INTERVAL = 4L; // 5x/sec — matches display interp
    private static final double RIGHT_DIST = 0.9;
    private static final double CHEST_Y_OFFSET = 1.2; // from player feet

    private final SilkMonadPlugin plugin;
    private final BalanceFetcher fetcher;
    private final TokenRegistry tokens;
    private final MerchantRegistry merchants;
    private final PlayerProfileStore profiles;
    private final Map<UUID, BalanceHologram> active = new HashMap<>();
    private BukkitTask tickTask;

    public HologramManager(SilkMonadPlugin plugin, BalanceFetcher fetcher, TokenRegistry tokens, MerchantRegistry merchants, PlayerProfileStore profiles) {
        this.plugin = plugin;
        this.fetcher = fetcher;
        this.tokens = tokens;
        this.merchants = merchants;
        this.profiles = profiles;
    }

    public void start() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    /** Fetches balances for the player's linked wallet and (re)builds their hologram. */
    public void refresh(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile.wallet() == null) {
            detach(player.getUniqueId());
            return;
        }
        BalanceHologram holo = active.computeIfAbsent(player.getUniqueId(),
                id -> new BalanceHologram(slotLocation(player), 0f));
        fetcher.fetch(profile.wallet()).whenComplete((balances, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                detach(player.getUniqueId());
                return;
            }
            BalanceHologram h = active.get(player.getUniqueId());
            if (h == null) return;
            if (error != null) {
                plugin.getLogger().warning("Balance fetch failed for " + player.getName() + ": " + error.getMessage());
                h.showError("balance error");
                return;
            }
            Merchant merchant = merchants.byAddress(profile.wallet());
            h.update(tokens.all(), balances, merchant);
        }));
    }

    public void detach(UUID uuid) {
        BalanceHologram h = active.remove(uuid);
        if (h != null) h.remove();
    }

    public void detachAll() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (BalanceHologram h : active.values()) h.remove();
        active.clear();
    }

    private void tick() {
        Iterator<Map.Entry<UUID, BalanceHologram>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BalanceHologram> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                e.getValue().remove();
                it.remove();
                continue;
            }
            e.getValue().setLocation(slotLocation(p));
        }
    }

    private static Location slotLocation(Player p) {
        Location loc = p.getLocation();
        double yawRad = Math.toRadians(loc.getYaw());
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);
        return new Location(loc.getWorld(),
                loc.getX() + rx * RIGHT_DIST,
                loc.getY() + CHEST_Y_OFFSET,
                loc.getZ() + rz * RIGHT_DIST);
    }
}
