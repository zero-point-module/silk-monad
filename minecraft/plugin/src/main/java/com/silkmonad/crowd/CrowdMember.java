package com.silkmonad.crowd;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CrowdMember {

    public enum State {
        ROAMING,
        SEEKING,
        CHATTING
    }

    public final Villager villager;
    public State state = State.ROAMING;
    /** Wall-clock millis when this state should end. */
    public long stateEndsAtMillis;
    @Nullable public UUID partnerUuid;
    @Nullable public Location moveTarget;

    public CrowdMember(Villager villager) {
        this.villager = villager;
    }
}
