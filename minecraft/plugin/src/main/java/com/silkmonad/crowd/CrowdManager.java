package com.silkmonad.crowd;

import com.silkmonad.SilkMonadPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns a configurable crowd of villagers around a center point and runs a
 * social-loop state machine: each one wanders for a while, then seeks the nearest
 * unpaired neighbour, walks over, faces them and emits particles/sounds as if
 * they were chatting, then drifts apart and starts over.
 *
 * Villagers are non-persistent — they'll despawn naturally if no players are
 * nearby, and we explicitly remove them on plugin shutdown or /silk crowd clear.
 */
public final class CrowdManager {

    /** How often the social-loop ticks (1s — villagers' own AI fills the gaps). */
    private static final long TICK_INTERVAL = 20L;

    /** Max distance to consider partners. Far-apart members keep roaming. */
    private static final double PAIR_RANGE_SQ = 32 * 32;

    /** Close enough to be "chatting". */
    private static final double CHAT_RANGE = 2.5;

    private final SilkMonadPlugin plugin;
    private final Map<UUID, CrowdMember> members = new HashMap<>();
    private BukkitTask tickTask;

    public CrowdManager(SilkMonadPlugin plugin) {
        this.plugin = plugin;
    }

    public int activeCount() {
        return members.size();
    }

    public void spawn(Location center, int count) {
        clear();
        World world = center.getWorld();
        if (world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Villager.Profession[] professions = Villager.Profession.values();

        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 3 + rng.nextDouble() * 18;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location spawnLoc = new Location(world, x + 0.5, y, z + 0.5);

            Villager v = world.spawn(spawnLoc, Villager.class, vil -> {
                vil.setProfession(professions[rng.nextInt(professions.length)]);
                vil.setVillagerLevel(1 + rng.nextInt(5));
                vil.setPersistent(false);
                vil.setRemoveWhenFarAway(false);
                vil.setAI(true);
                vil.setAware(true);
                vil.setCustomNameVisible(false);
                // Lower volume / less chatter on default villager AI.
                vil.setCanPickupItems(false);
            });

            CrowdMember m = new CrowdMember(v);
            m.stateEndsAtMillis = System.currentTimeMillis() + roamDurationMillis(rng);
            members.put(v.getUniqueId(), m);
        }

        startTickLoop();
    }

    public void clear() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (CrowdMember m : members.values()) {
            if (m.villager.isValid()) m.villager.remove();
        }
        members.clear();
    }

    private void startTickLoop() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Prune dead/missing villagers.
        members.values().removeIf(m -> !m.villager.isValid());
        if (members.isEmpty()) {
            clear();
            return;
        }

        // Advance each member's behaviour. Collect seekers for pairing.
        List<CrowdMember> seekers = new ArrayList<>();
        for (CrowdMember m : members.values()) {
            switch (m.state) {
                case ROAMING -> tickRoaming(m, now, rng);
                case SEEKING -> seekers.add(m);
                case CHATTING -> tickChatting(m, now, rng);
            }
            if (m.state == CrowdMember.State.SEEKING && !seekers.contains(m)) seekers.add(m);
        }

        pairUpSeekers(seekers, rng);
    }

    private void tickRoaming(CrowdMember m, long now, ThreadLocalRandom rng) {
        Location current = m.villager.getLocation();
        if (m.moveTarget == null || current.distanceSquared(m.moveTarget) < 4 || rng.nextInt(8) == 0) {
            m.moveTarget = randomNearby(current, 4, 12);
            m.villager.getPathfinder().moveTo(m.moveTarget, 0.45);
        }
        if (now >= m.stateEndsAtMillis) {
            m.state = CrowdMember.State.SEEKING;
            m.moveTarget = null;
        }
    }

    private void tickChatting(CrowdMember m, long now, ThreadLocalRandom rng) {
        CrowdMember partner = m.partnerUuid != null ? members.get(m.partnerUuid) : null;
        if (partner == null || partner.partnerUuid == null
                || !partner.partnerUuid.equals(m.villager.getUniqueId())
                || partner.state != CrowdMember.State.CHATTING) {
            // Partner gone / out of sync — fall back to seeking.
            m.state = CrowdMember.State.SEEKING;
            m.partnerUuid = null;
            return;
        }

        Location mine = m.villager.getLocation();
        Location theirs = partner.villager.getLocation();
        double dist = mine.distance(theirs);

        if (dist > CHAT_RANGE) {
            // Walk toward partner.
            m.villager.getPathfinder().moveTo(theirs, 0.55);
        } else {
            // Stop and face them.
            Vector toThem = theirs.toVector().subtract(mine.toVector());
            if (toThem.lengthSquared() > 1e-6) {
                m.villager.lookAt(theirs.getX(), theirs.getY() + 1.5, theirs.getZ());
            }

            // Periodic chatter effects — only one side emits to avoid double-firing.
            if (m.villager.getUniqueId().compareTo(partner.villager.getUniqueId()) < 0) {
                if (rng.nextInt(4) == 0) {
                    Location head = mine.clone().add(0, 1.8, 0);
                    Location theirHead = theirs.clone().add(0, 1.8, 0);
                    Location midpoint = head.clone().add(theirHead).multiply(0.5);
                    midpoint.setWorld(mine.getWorld());
                    mine.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, midpoint, 3, 0.25, 0.25, 0.25);
                }
                if (rng.nextInt(10) == 0) {
                    mine.getWorld().playSound(mine, Sound.ENTITY_VILLAGER_AMBIENT, 0.35f,
                            0.9f + rng.nextFloat() * 0.3f);
                }
            }
        }

        if (now >= m.stateEndsAtMillis) {
            m.state = CrowdMember.State.ROAMING;
            m.partnerUuid = null;
            m.stateEndsAtMillis = now + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    private void pairUpSeekers(List<CrowdMember> seekers, ThreadLocalRandom rng) {
        Set<UUID> unpaired = new HashSet<>();
        for (CrowdMember s : seekers) unpaired.add(s.villager.getUniqueId());

        for (CrowdMember m : seekers) {
            if (!unpaired.contains(m.villager.getUniqueId())) continue;
            CrowdMember best = null;
            double bestDistSq = Double.MAX_VALUE;
            Location mine = m.villager.getLocation();
            for (CrowdMember other : seekers) {
                if (other == m) continue;
                if (!unpaired.contains(other.villager.getUniqueId())) continue;
                double dsq = mine.distanceSquared(other.villager.getLocation());
                if (dsq < bestDistSq) {
                    bestDistSq = dsq;
                    best = other;
                }
            }
            if (best != null && bestDistSq <= PAIR_RANGE_SQ) {
                long chatEnd = System.currentTimeMillis() + chatDurationMillis(rng);
                m.state = CrowdMember.State.CHATTING;
                best.state = CrowdMember.State.CHATTING;
                m.partnerUuid = best.villager.getUniqueId();
                best.partnerUuid = m.villager.getUniqueId();
                m.stateEndsAtMillis = chatEnd;
                best.stateEndsAtMillis = chatEnd;
                unpaired.remove(m.villager.getUniqueId());
                unpaired.remove(best.villager.getUniqueId());
            }
        }

        // Anyone still unpaired: drop back to ROAMING so they wander a bit then try again.
        for (CrowdMember m : seekers) {
            if (m.state != CrowdMember.State.SEEKING) continue;
            m.state = CrowdMember.State.ROAMING;
            m.stateEndsAtMillis = System.currentTimeMillis() + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    private Location randomNearby(Location center, double minDist, double maxDist) {
        World world = center.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = minDist + rng.nextDouble() * (maxDist - minDist);
        int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private static long roamDurationMillis(ThreadLocalRandom rng) {
        return 12_000L + rng.nextLong(18_000L); // 12–30s wandering
    }

    private static long chatDurationMillis(ThreadLocalRandom rng) {
        return 12_000L + rng.nextLong(15_000L); // 12–27s chatting
    }
}
