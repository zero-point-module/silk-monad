package com.silkmonad.crowd;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.Token;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.cosmetic.Cosmetic;
import com.silkmonad.cosmetic.CosmeticRegistry;
import com.silkmonad.cosmetic.item.ItemCosmetic;
import com.silkmonad.hologram.BalanceHologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crowd of villager NPCs each with mock per-token balances. They wander, find a
 * partner, walk over, and act out a trade by dropping cosmetic token items on
 * the ground that the partner walks to and "picks up" (we remove the entity).
 * Each NPC has a balance hologram above their head that updates live as their
 * mock balances change.
 *
 * Nothing here touches the chain — it's a visual sandbox.
 */
public final class CrowdManager {

    private static final long TICK_INTERVAL = 20L; // 1s
    private static final double PAIR_RANGE_SQ = 32 * 32;
    private static final double CHAT_RANGE = 2.5;
    private static final double PICKUP_RANGE_SQ = 1.6 * 1.6;

    private final SilkMonadPlugin plugin;
    private final TokenRegistry tokens;
    private final CosmeticRegistry cosmetics;
    private final Map<UUID, CrowdMember> members = new HashMap<>();
    private BukkitTask tickTask;

    public CrowdManager(SilkMonadPlugin plugin, TokenRegistry tokens, CosmeticRegistry cosmetics) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.cosmetics = cosmetics;
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
                vil.setCanPickupItems(false);
                vil.setSilent(true); // we'll make our own trade sounds
            });

            BalanceHologram hologram = new BalanceHologram(v, 0.4f);
            CrowdMember m = new CrowdMember(v, hologram);
            seedMockBalances(m, rng);
            updateHologram(m);
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
            if (m.pendingOfferItem != null && m.pendingOfferItem.isValid()) {
                m.pendingOfferItem.remove();
            }
            if (m.hologram != null) m.hologram.remove();
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

        members.values().removeIf(m -> {
            if (!m.villager.isValid()) {
                if (m.hologram != null) m.hologram.remove();
                if (m.pendingOfferItem != null && m.pendingOfferItem.isValid()) m.pendingOfferItem.remove();
                return true;
            }
            return false;
        });
        if (members.isEmpty()) {
            clear();
            return;
        }

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
            abandonTrade(m);
            m.state = CrowdMember.State.SEEKING;
            m.partnerUuid = null;
            return;
        }

        Location mine = m.villager.getLocation();
        Location theirs = partner.villager.getLocation();
        double dist = mine.distance(theirs);

        // Attempt to pick up the partner's pending offer if we're close to it
        if (partner.pendingOfferItem != null && partner.pendingOfferItem.isValid()) {
            double itemDistSq = mine.distanceSquared(partner.pendingOfferItem.getLocation());
            if (itemDistSq <= PICKUP_RANGE_SQ) {
                consumeOffer(m, partner);
            } else if (dist > CHAT_RANGE) {
                // Walk towards the dropped item rather than the partner themselves
                m.villager.getPathfinder().moveTo(partner.pendingOfferItem.getLocation(), 0.55);
            }
        } else if (dist > CHAT_RANGE) {
            m.villager.getPathfinder().moveTo(theirs, 0.55);
        } else {
            // Close enough — face partner.
            m.villager.lookAt(theirs.getX(), theirs.getY() + 1.5, theirs.getZ());

            // The "lesser UUID" side decides when to drop the offer, so we don't double-drop.
            boolean iAmLeader = m.villager.getUniqueId().compareTo(partner.villager.getUniqueId()) < 0;
            if (iAmLeader && m.pendingOfferItem == null && partner.pendingOfferItem == null) {
                // Both haven't offered yet — leader drops first.
                dropOffer(m, rng);
            } else if (!iAmLeader && m.pendingOfferItem == null && partner.pendingOfferItem != null) {
                // Follower drops their counter-offer once they see the leader's offer.
                dropOffer(m, rng);
            }

            if (iAmLeader && rng.nextInt(8) == 0) {
                Location head = mine.clone().add(0, 1.8, 0);
                Location theirHead = theirs.clone().add(0, 1.8, 0);
                Location midpoint = head.clone().add(theirHead).multiply(0.5);
                midpoint.setWorld(mine.getWorld());
                mine.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, midpoint, 3, 0.25, 0.25, 0.25);
            }
        }

        if (now >= m.stateEndsAtMillis) {
            // End of chat: refund any unconsumed offer, reset state.
            abandonTrade(m);
            m.state = CrowdMember.State.ROAMING;
            m.partnerUuid = null;
            m.stateEndsAtMillis = now + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    private void dropOffer(CrowdMember m, ThreadLocalRandom rng) {
        if (tokens.all().isEmpty() || cosmetics.size() == 0) return;
        // Pick a token from those they actually hold a non-zero balance of.
        List<Token> candidates = new ArrayList<>();
        for (Token t : tokens.all()) {
            BigDecimal bal = m.mockBalances.getOrDefault(t.symbol(), BigDecimal.ZERO);
            if (bal.compareTo(BigDecimal.ONE) >= 0) candidates.add(t);
        }
        if (candidates.isEmpty()) return;
        Token chosen = candidates.get(rng.nextInt(candidates.size()));
        BigDecimal owned = m.mockBalances.get(chosen.symbol());
        int maxOffer = Math.min(owned.intValue(), 8);
        int amount = 1 + rng.nextInt(Math.max(1, maxOffer));

        Optional<Cosmetic> cosmetic = cosmetics.get(chosen.symbol().toLowerCase(Locale.ROOT));
        if (cosmetic.isEmpty() || !(cosmetic.get() instanceof ItemCosmetic ic)) return;

        ItemStack stack = ic.newStack(amount);
        Location dropLoc = m.villager.getLocation().add(0, 1.0, 0);
        Item entity = m.villager.getWorld().dropItem(dropLoc, stack);
        entity.setPickupDelay(Integer.MAX_VALUE); // no real player can grab it
        entity.setUnlimitedLifetime(true);
        // Toss it gently toward the partner so it lands between them
        CrowdMember partner = m.partnerUuid != null ? members.get(m.partnerUuid) : null;
        if (partner != null) {
            Location target = partner.villager.getLocation();
            org.bukkit.util.Vector toward = target.toVector().subtract(m.villager.getLocation().toVector()).normalize().multiply(0.2);
            toward.setY(0.15);
            entity.setVelocity(toward);
        }

        m.pendingOfferItem = entity;
        m.pendingOfferToken = chosen.symbol();
        m.pendingOfferAmount = amount;
        m.mockBalances.merge(chosen.symbol(), BigDecimal.valueOf(amount), BigDecimal::subtract);
        updateHologram(m);

        m.villager.getWorld().playSound(m.villager.getLocation(),
                Sound.ENTITY_ITEM_PICKUP, 0.4f, 0.7f);
    }

    private void consumeOffer(CrowdMember consumer, CrowdMember giver) {
        if (giver.pendingOfferItem == null || giver.pendingOfferToken == null) return;
        Item item = giver.pendingOfferItem;
        String token = giver.pendingOfferToken;
        int amount = giver.pendingOfferAmount;

        consumer.mockBalances.merge(token, BigDecimal.valueOf(amount), BigDecimal::add);
        updateHologram(consumer);

        // Visual + audio
        Location at = consumer.villager.getLocation().add(0, 1.0, 0);
        consumer.villager.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.3, 0.3, 0.3);
        consumer.villager.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);

        if (item.isValid()) item.remove();
        giver.pendingOfferItem = null;
        giver.pendingOfferToken = null;
        giver.pendingOfferAmount = 0;
    }

    private void abandonTrade(CrowdMember m) {
        if (m.pendingOfferItem != null) {
            // Refund balance since trade didn't complete.
            if (m.pendingOfferToken != null) {
                m.mockBalances.merge(m.pendingOfferToken, BigDecimal.valueOf(m.pendingOfferAmount), BigDecimal::add);
                updateHologram(m);
            }
            if (m.pendingOfferItem.isValid()) m.pendingOfferItem.remove();
            m.pendingOfferItem = null;
            m.pendingOfferToken = null;
            m.pendingOfferAmount = 0;
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

        for (CrowdMember m : seekers) {
            if (m.state != CrowdMember.State.SEEKING) continue;
            m.state = CrowdMember.State.ROAMING;
            m.stateEndsAtMillis = System.currentTimeMillis() + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    private void seedMockBalances(CrowdMember m, ThreadLocalRandom rng) {
        for (Token t : tokens.all()) {
            m.mockBalances.put(t.symbol(), BigDecimal.valueOf(50 + rng.nextInt(450)));
        }
    }

    private void updateHologram(CrowdMember m) {
        if (m.hologram == null) return;
        m.hologram.update(tokens.all(), m.mockBalances, null);
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
        return 12_000L + rng.nextLong(18_000L);
    }

    private static long chatDurationMillis(ThreadLocalRandom rng) {
        return 12_000L + rng.nextLong(15_000L);
    }
}
