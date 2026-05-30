package com.silkmonad.listeners;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.BalanceFetcher;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.chain.TransactionFetcher;
import com.silkmonad.gui.AgentInfoGui;
import com.silkmonad.merchant.MerchantRegistry;
import com.silkmonad.profile.PlayerProfile;
import com.silkmonad.profile.PlayerProfileStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class AgentInteractListener implements Listener {

    private final SilkMonadPlugin plugin;
    private final TokenRegistry tokens;
    private final MerchantRegistry merchants;
    private final PlayerProfileStore profiles;
    private final BalanceFetcher balanceFetcher;
    private final TransactionFetcher txFetcher;

    public AgentInteractListener(SilkMonadPlugin plugin,
                                 TokenRegistry tokens,
                                 MerchantRegistry merchants,
                                 PlayerProfileStore profiles,
                                 BalanceFetcher balanceFetcher,
                                 TransactionFetcher txFetcher) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.merchants = merchants;
        this.profiles = profiles;
        this.balanceFetcher = balanceFetcher;
        this.txFetcher = txFetcher;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        PlayerProfile profile = profiles.get(target.getUniqueId());
        if (profile.wallet() == null) return;

        Player viewer = event.getPlayer();
        AgentInfoGui gui = new AgentInfoGui(plugin, tokens, merchants, profiles,
                target.getUniqueId(), target.getName(), profile.wallet());
        viewer.openInventory(gui.getInventory());

        // Async chain reads, hop back to main thread to mutate the GUI.
        balanceFetcher.fetch(profile.wallet()).whenComplete((balances, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        gui.setError("Balance error: " + err.getMessage());
                        return;
                    }
                    gui.setBalances(balances);
                }));

        txFetcher.fetchInvolving(profile.wallet()).whenComplete((events, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        plugin.getLogger().warning("Tx fetch failed: " + err.getMessage());
                        gui.setError("Could not load transactions");
                        return;
                    }
                    gui.setEvents(events);
                }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AgentInfoGui gui)) return;
        // Block all item movement
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        gui.onSlotClicked(viewer, event.getSlot(), event.getClick());
    }
}
