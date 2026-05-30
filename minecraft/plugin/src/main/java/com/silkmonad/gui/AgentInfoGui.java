package com.silkmonad.gui;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.Token;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.chain.TransferEvent;
import com.silkmonad.merchant.Merchant;
import com.silkmonad.merchant.MerchantRegistry;
import com.silkmonad.profile.PlayerProfileStore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 6x9 chest GUI shown when a player right-clicks a wallet-linked player.
 *
 * Layout:
 *   row 0   : holdings (one token icon per slot, centered)
 *   row 1-4 : up to 36 paginated Transfer events, newest first
 *   row 5   : navigation (prev | page # | next)
 */
public final class AgentInfoGui implements InventoryHolder {

    private static final Key SILK_FONT = Key.key("silk", "default");
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.####");
    private static final int PAGE_SIZE = 36;
    private static final int PREV_SLOT = 45;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final String TX_HASH_KEY = "tx_hash";

    private final SilkMonadPlugin plugin;
    private final TokenRegistry tokens;
    private final MerchantRegistry merchants;
    private final PlayerProfileStore profiles;
    private final UUID targetUuid;
    private final String targetName;
    private final String walletAddress;
    private final Inventory inventory;
    private List<TransferEvent> events = Collections.emptyList();
    private int page = 0;

    public AgentInfoGui(SilkMonadPlugin plugin,
                        TokenRegistry tokens,
                        MerchantRegistry merchants,
                        PlayerProfileStore profiles,
                        UUID targetUuid,
                        String targetName,
                        String walletAddress) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.merchants = merchants;
        this.profiles = profiles;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.walletAddress = walletAddress;

        Merchant merchant = merchants.byAddress(walletAddress);
        Component title = Component.text(merchant != null ? merchant.name() : targetName,
                merchant != null && merchant.color() != null ? merchant.color() : NamedTextColor.GOLD,
                TextDecoration.BOLD);
        this.inventory = Bukkit.createInventory(this, 54, title);
        renderShell();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String walletAddress() {
        return walletAddress;
    }

    public void setBalances(Map<String, BigDecimal> balances) {
        List<Token> all = tokens.all();
        int n = all.size();
        // Center the icons across slots 0..8.
        int start = (9 - n) / 2;
        for (int i = 0; i < n; i++) {
            Token t = all.get(i);
            BigDecimal amount = balances.getOrDefault(t.symbol(), BigDecimal.ZERO);
            inventory.setItem(start + i, holdingItem(t, amount));
        }
    }

    public void setEvents(List<TransferEvent> events) {
        this.events = events;
        this.page = 0;
        renderPage();
    }

    public void setError(String message) {
        for (int i = 9; i < 45; i++) inventory.setItem(i, null);
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(message, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        inventory.setItem(22, item);
    }

    public void onSlotClicked(Player viewer, int slot, ClickType click) {
        if (slot == PREV_SLOT) {
            if (page > 0) {
                page--;
                renderPage();
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            int total = totalPages();
            if (page < total - 1) {
                page++;
                renderPage();
            }
            return;
        }
        // Transaction item — send a clickable explorer link to the viewer.
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String hash = meta.getPersistentDataContainer().get(plugin.key(TX_HASH_KEY), PersistentDataType.STRING);
        if (hash == null) return;
        String url = "https://monad-testnet.socialscan.io/tx/" + hash;
        viewer.sendMessage(Component.text("Transaction: ", NamedTextColor.GRAY)
                .append(Component.text(hash.substring(0, 10) + "…", NamedTextColor.YELLOW)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("Open in explorer", NamedTextColor.GRAY)))));
    }

    // ---------- rendering ----------

    private void renderShell() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty());
        pane.setItemMeta(paneMeta);

        for (int i = 0; i < 9; i++) inventory.setItem(i, pane);
        for (int i = 45; i < 54; i++) inventory.setItem(i, pane);

        // Loading indicator for tx area
        ItemStack loading = new ItemStack(Material.CLOCK);
        ItemMeta lMeta = loading.getItemMeta();
        lMeta.displayName(Component.text("Loading transactions…", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        loading.setItemMeta(lMeta);
        inventory.setItem(22, loading);
    }

    private void renderPage() {
        // Clear the tx area
        for (int i = 9; i < 45; i++) inventory.setItem(i, null);
        if (events.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta m = empty.getItemMeta();
            m.displayName(Component.text("No trades yet", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            empty.setItemMeta(m);
            inventory.setItem(22, empty);
        } else {
            int from = page * PAGE_SIZE;
            int to = Math.min(events.size(), from + PAGE_SIZE);
            int slot = 9;
            for (int i = from; i < to; i++) {
                inventory.setItem(slot++, txItem(events.get(i)));
            }
        }
        renderNav();
    }

    private void renderNav() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.empty());
        pane.setItemMeta(paneMeta);
        for (int i = 45; i < 54; i++) inventory.setItem(i, pane);

        int total = totalPages();

        ItemStack prev = new ItemStack(page > 0 ? Material.ARROW : Material.RED_STAINED_GLASS_PANE);
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.displayName(Component.text(page > 0 ? "← Previous" : "(no previous page)",
                page > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        prev.setItemMeta(prevMeta);
        inventory.setItem(PREV_SLOT, prev);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text(String.format("Page %d / %d", page + 1, total), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Wallet: " + shortenAddress(walletAddress), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(events.size() + " total transactions", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inventory.setItem(PAGE_INFO_SLOT, info);

        boolean hasNext = page < total - 1;
        ItemStack next = new ItemStack(hasNext ? Material.ARROW : Material.RED_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.displayName(Component.text(hasNext ? "Next →" : "(no next page)",
                hasNext ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        next.setItemMeta(nextMeta);
        inventory.setItem(NEXT_SLOT, next);
    }

    private int totalPages() {
        if (events.isEmpty()) return 1;
        return (int) Math.ceil((double) events.size() / PAGE_SIZE);
    }

    private ItemStack holdingItem(Token token, BigDecimal amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        TextColor color = token.color() != null ? token.color() : NamedTextColor.WHITE;
        Component name = Component.empty();
        if (token.glyph() != null && !token.glyph().isEmpty()) {
            name = name.append(Component.text(token.glyph()).font(SILK_FONT)).append(Component.text(" "));
        }
        name = name.append(Component.text(token.symbol(), color))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(FMT.format(amount), NamedTextColor.WHITE));
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack txItem(TransferEvent event) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        boolean outgoing = event.from().equalsIgnoreCase(walletAddress);
        String counterpartyAddr = outgoing ? event.to() : event.from();
        Component counterparty = counterpartyName(counterpartyAddr);
        BigDecimal amount = new BigDecimal(event.rawAmount()).movePointLeft(event.token().decimals());
        TextColor color = event.token().color() != null ? event.token().color() : NamedTextColor.WHITE;
        TextColor signColor = outgoing ? NamedTextColor.RED : NamedTextColor.GREEN;
        String signStr = outgoing ? "-" : "+";

        Component name = Component.empty();
        if (event.token().glyph() != null && !event.token().glyph().isEmpty()) {
            name = name.append(Component.text(event.token().glyph()).font(SILK_FONT)).append(Component.text(" "));
        }
        name = name.append(Component.text(signStr + FMT.format(amount) + " " + event.token().symbol(), signColor))
                .append(Component.text(outgoing ? " → " : " ← ", NamedTextColor.GRAY))
                .append(counterparty);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Token: ", NamedTextColor.GRAY)
                .append(Component.text(event.token().symbol(), color))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Block #" + event.blockNumber(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(shortenHash(event.txHash()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click for explorer link", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(plugin.key(TX_HASH_KEY), PersistentDataType.STRING, event.txHash());
        item.setItemMeta(meta);
        return item;
    }

    private Component counterpartyName(String address) {
        // Zero address = mint/burn
        if (address.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            return Component.text("(treasury)", NamedTextColor.GOLD);
        }
        Merchant m = merchants.byAddress(address);
        if (m != null) {
            return Component.text(m.name(), m.color() != null ? m.color() : NamedTextColor.WHITE);
        }
        Player online = findOnlinePlayerByWallet(address);
        if (online != null) {
            return Component.text(online.getName(), NamedTextColor.AQUA);
        }
        return Component.text(shortenAddress(address), NamedTextColor.GRAY);
    }

    private Player findOnlinePlayerByWallet(String address) {
        String needle = address.toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String w = profiles.get(p.getUniqueId()).wallet();
            if (w != null && w.toLowerCase(Locale.ROOT).equals(needle)) return p;
        }
        return null;
    }

    private static String shortenAddress(String address) {
        if (address.length() < 12) return address;
        return address.substring(0, 6) + "…" + address.substring(address.length() - 4);
    }

    private static String shortenHash(String hash) {
        if (hash.length() < 18) return hash;
        return hash.substring(0, 10) + "…" + hash.substring(hash.length() - 6);
    }
}
