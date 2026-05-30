package com.silkmonad.hologram;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.BalanceFetcher;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.merchant.Merchant;
import com.silkmonad.merchant.MerchantRegistry;
import com.silkmonad.profile.PlayerProfile;
import com.silkmonad.profile.PlayerProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HologramManager {

    private final SilkMonadPlugin plugin;
    private final BalanceFetcher fetcher;
    private final TokenRegistry tokens;
    private final MerchantRegistry merchants;
    private final PlayerProfileStore profiles;
    private final Map<UUID, BalanceHologram> active = new HashMap<>();

    public HologramManager(SilkMonadPlugin plugin, BalanceFetcher fetcher, TokenRegistry tokens, MerchantRegistry merchants, PlayerProfileStore profiles) {
        this.plugin = plugin;
        this.fetcher = fetcher;
        this.tokens = tokens;
        this.merchants = merchants;
        this.profiles = profiles;
    }

    /** Fetches balances for the player's linked wallet and (re)builds their hologram. */
    public void refresh(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile.wallet() == null) {
            detach(player.getUniqueId());
            return;
        }
        BalanceHologram holo = active.computeIfAbsent(player.getUniqueId(), id -> new BalanceHologram(player));
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
        for (BalanceHologram h : active.values()) h.remove();
        active.clear();
    }
}
