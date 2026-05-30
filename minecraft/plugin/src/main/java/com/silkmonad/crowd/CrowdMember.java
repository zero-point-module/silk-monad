package com.silkmonad.crowd;

import com.silkmonad.hologram.BalanceHologram;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CrowdMember {

    public enum State {
        ROAMING,
        SEEKING,
        CHATTING
    }

    public final Villager villager;
    /** Mock per-token balance, completely independent of any on-chain state. */
    public final Map<String, BigDecimal> mockBalances = new LinkedHashMap<>();
    public final BalanceHologram hologram;

    public State state = State.ROAMING;
    /** Wall-clock millis when this state should end. */
    public long stateEndsAtMillis;
    @Nullable public UUID partnerUuid;
    @Nullable public Location moveTarget;

    /** The item this NPC has dropped as their offer in the current trade (null when idle). */
    @Nullable public Item pendingOfferItem;
    @Nullable public String pendingOfferToken;
    public int pendingOfferAmount;

    public CrowdMember(Villager villager, BalanceHologram hologram) {
        this.villager = villager;
        this.hologram = hologram;
    }
}
