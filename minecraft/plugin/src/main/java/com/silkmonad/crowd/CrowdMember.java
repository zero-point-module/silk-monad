package com.silkmonad.crowd;

import com.silkmonad.hologram.BalanceHologram;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CrowdMember {

    public enum State {
        ROAMING,
        SEEKING,
        CHATTING
    }

    /** Internal id used by FancyNpcs (== npc.getData().getName()). */
    public final String id;
    public final Npc npc;
    public final BalanceHologram hologram;
    public final Map<String, BigDecimal> mockBalances = new LinkedHashMap<>();

    public Location currentLocation;
    public State state = State.ROAMING;
    public long stateEndsAtMillis;
    @Nullable public Location moveTarget;
    @Nullable public String partnerId;

    /** Active token offer dropped on the ground (null when idle). */
    @Nullable public Item pendingOfferItem;
    @Nullable public String pendingOfferToken;
    public int pendingOfferAmount;

    public CrowdMember(String id, Npc npc, BalanceHologram hologram, Location startLocation) {
        this.id = id;
        this.npc = npc;
        this.hologram = hologram;
        this.currentLocation = startLocation;
    }
}
