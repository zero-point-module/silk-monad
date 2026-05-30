package com.silkmonad.crowd;

import com.silkmonad.SilkMonadPlugin;
import com.silkmonad.chain.Token;
import com.silkmonad.chain.TokenRegistry;
import com.silkmonad.cosmetic.Cosmetic;
import com.silkmonad.cosmetic.CosmeticRegistry;
import com.silkmonad.cosmetic.item.ItemCosmetic;
import com.silkmonad.hologram.BalanceHologram;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * Crowd of FancyNpcs player-skinned NPCs with mock per-token balances. They
 * wander, find a partner, walk over, and act out a trade by dropping cosmetic
 * token items that the partner walks to and "picks up" (we remove the entity
 * and credit their balance). Each NPC has a balance hologram that follows it.
 *
 * Nothing here touches the chain — purely a visual sandbox.
 *
 * Requires FancyNpcs to be installed (softdepend in plugin.yml). If missing,
 * {@link #spawn} logs a warning and no-ops.
 */
public final class CrowdManager {

    private static final long MOVE_TICK_INTERVAL = 4L;   // 5x/sec — smooth walking
    private static final double STEP_DISTANCE = 0.22;    // blocks per move tick
    private static final double PAIR_RANGE_SQ = 32 * 32;
    private static final double CHAT_RANGE = 2.2;
    private static final double PICKUP_RANGE_SQ = 1.6 * 1.6;
    private static final double ARRIVAL_THRESHOLD_SQ = 0.4 * 0.4;

    /** Skin pool — well-known Minecraft accounts that resolve cleanly via Mojang. */
    private static final String[] SKIN_POOL = {
            "Notch", "jeb_", "Dinnerbone", "Grumm",
            "Technoblade", "Hypixel", "PaperMC", "MojangSupport",
            "Mojang", "MHF_Steve", "MHF_Alex"
    };

    private final SilkMonadPlugin plugin;
    private final TokenRegistry tokens;
    private final CosmeticRegistry cosmetics;
    private final Map<String, CrowdMember> members = new HashMap<>();
    private BukkitTask tickTask;

    public CrowdManager(SilkMonadPlugin plugin, TokenRegistry tokens, CosmeticRegistry cosmetics) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.cosmetics = cosmetics;
    }

    public int activeCount() {
        return members.size();
    }

    public boolean isFancyNpcsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
    }

    public void spawn(Location center, int count, UUID creatorUuid) {
        if (!isFancyNpcsAvailable()) {
            plugin.getLogger().warning("/silk crowd skipped — FancyNpcs plugin is not loaded.");
            return;
        }
        clear();
        World world = center.getWorld();
        if (world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        NpcManager mgr = FancyNpcsPlugin.get().getNpcManager();

        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 3 + rng.nextDouble() * 18;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location spawnLoc = new Location(world, x + 0.5, y, z + 0.5,
                    rng.nextFloat() * 360f - 180f, 0f);

            String id = "silk_crowd_" + System.nanoTime() + "_" + i;
            NpcData data = new NpcData(id, creatorUuid, spawnLoc);
            data.setShowInTab(false);
            data.setCollidable(false);
            data.setSkin(SKIN_POOL[rng.nextInt(SKIN_POOL.length)]);
            data.setType(EntityType.PLAYER);
            data.setTurnToPlayer(false);

            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.setSaveToFile(false);
            mgr.registerNpc(npc);
            npc.create();
            npc.spawnForAll();

            BalanceHologram hologram = new BalanceHologram(slotLocation(spawnLoc), 0f);
            CrowdMember m = new CrowdMember(id, npc, hologram, spawnLoc.clone());
            seedMockBalances(m, rng);
            updateHologram(m);
            m.stateEndsAtMillis = System.currentTimeMillis() + roamDurationMillis(rng);
            members.put(id, m);
        }

        startTickLoop();
    }

    public void clear() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        boolean fancyOnline = isFancyNpcsAvailable();
        NpcManager mgr = fancyOnline ? FancyNpcsPlugin.get().getNpcManager() : null;
        for (CrowdMember m : members.values()) {
            if (m.pendingOfferItem != null && m.pendingOfferItem.isValid()) m.pendingOfferItem.remove();
            if (m.hologram != null) m.hologram.remove();
            if (fancyOnline && m.npc != null) {
                m.npc.removeForAll();
                mgr.removeNpc(m.npc);
            }
        }
        members.clear();
    }

    private void startTickLoop() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, MOVE_TICK_INTERVAL, MOVE_TICK_INTERVAL);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        List<CrowdMember> seekers = new ArrayList<>();
        for (CrowdMember m : members.values()) {
            switch (m.state) {
                case ROAMING -> tickRoaming(m, now, rng);
                case SEEKING -> seekers.add(m);
                case CHATTING -> tickChatting(m, now, rng);
            }
            if (m.state == CrowdMember.State.SEEKING && !seekers.contains(m)) seekers.add(m);
            stepHologram(m);
        }

        pairUpSeekers(seekers, rng);
    }

    // ---------- state handlers ----------

    private void tickRoaming(CrowdMember m, long now, ThreadLocalRandom rng) {
        if (m.moveTarget == null || m.currentLocation.distanceSquared(m.moveTarget) < ARRIVAL_THRESHOLD_SQ) {
            m.moveTarget = randomNearby(m.currentLocation, 4, 12, rng);
        }
        stepToward(m, m.moveTarget);
        if (now >= m.stateEndsAtMillis) {
            m.state = CrowdMember.State.SEEKING;
            m.moveTarget = null;
        }
    }

    private void tickChatting(CrowdMember m, long now, ThreadLocalRandom rng) {
        CrowdMember partner = m.partnerId != null ? members.get(m.partnerId) : null;
        if (partner == null || partner.partnerId == null
                || !partner.partnerId.equals(m.id)
                || partner.state != CrowdMember.State.CHATTING) {
            abandonTrade(m);
            m.state = CrowdMember.State.SEEKING;
            m.partnerId = null;
            return;
        }

        Location mine = m.currentLocation;
        Location theirs = partner.currentLocation;

        // If partner has an unclaimed offer on the ground close to me, take it.
        if (partner.pendingOfferItem != null && partner.pendingOfferItem.isValid()) {
            double itemDistSq = mine.distanceSquared(partner.pendingOfferItem.getLocation());
            if (itemDistSq <= PICKUP_RANGE_SQ) {
                consumeOffer(m, partner);
            } else {
                stepToward(m, partner.pendingOfferItem.getLocation());
            }
        } else {
            double dist = mine.distance(theirs);
            if (dist > CHAT_RANGE) {
                stepToward(m, theirs);
            } else {
                lookToward(m, theirs);
                // The lower-id side leads the trade so we don't double-drop.
                boolean iAmLeader = m.id.compareTo(partner.id) < 0;
                if (iAmLeader && m.pendingOfferItem == null && partner.pendingOfferItem == null) {
                    dropOffer(m, rng);
                } else if (!iAmLeader && m.pendingOfferItem == null && partner.pendingOfferItem != null) {
                    dropOffer(m, rng);
                }
                if (iAmLeader && rng.nextInt(20) == 0) {
                    Location head = mine.clone().add(0, 1.8, 0);
                    Location theirHead = theirs.clone().add(0, 1.8, 0);
                    Location midpoint = head.clone().add(theirHead).multiply(0.5);
                    midpoint.setWorld(mine.getWorld());
                    mine.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, midpoint, 3, 0.25, 0.25, 0.25);
                }
            }
        }

        if (now >= m.stateEndsAtMillis) {
            abandonTrade(m);
            m.state = CrowdMember.State.ROAMING;
            m.partnerId = null;
            m.stateEndsAtMillis = now + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    private void pairUpSeekers(List<CrowdMember> seekers, ThreadLocalRandom rng) {
        Set<String> unpaired = new HashSet<>();
        for (CrowdMember s : seekers) unpaired.add(s.id);

        for (CrowdMember m : seekers) {
            if (!unpaired.contains(m.id)) continue;
            CrowdMember best = null;
            double bestDistSq = Double.MAX_VALUE;
            Location mine = m.currentLocation;
            for (CrowdMember other : seekers) {
                if (other == m) continue;
                if (!unpaired.contains(other.id)) continue;
                double dsq = mine.distanceSquared(other.currentLocation);
                if (dsq < bestDistSq) {
                    bestDistSq = dsq;
                    best = other;
                }
            }
            if (best != null && bestDistSq <= PAIR_RANGE_SQ) {
                long chatEnd = System.currentTimeMillis() + chatDurationMillis(rng);
                m.state = CrowdMember.State.CHATTING;
                best.state = CrowdMember.State.CHATTING;
                m.partnerId = best.id;
                best.partnerId = m.id;
                m.stateEndsAtMillis = chatEnd;
                best.stateEndsAtMillis = chatEnd;
                unpaired.remove(m.id);
                unpaired.remove(best.id);
            }
        }

        for (CrowdMember m : seekers) {
            if (m.state != CrowdMember.State.SEEKING) continue;
            m.state = CrowdMember.State.ROAMING;
            m.stateEndsAtMillis = System.currentTimeMillis() + roamDurationMillis(rng);
            m.moveTarget = null;
        }
    }

    // ---------- trade mechanics ----------

    private void dropOffer(CrowdMember m, ThreadLocalRandom rng) {
        if (tokens.all().isEmpty() || cosmetics.size() == 0) return;
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
        Location dropLoc = m.currentLocation.clone().add(0, 1.0, 0);
        Item entity = dropLoc.getWorld().dropItem(dropLoc, stack);
        entity.setPickupDelay(Integer.MAX_VALUE);
        entity.setUnlimitedLifetime(true);

        CrowdMember partner = m.partnerId != null ? members.get(m.partnerId) : null;
        if (partner != null) {
            Vector toward = partner.currentLocation.toVector().subtract(m.currentLocation.toVector())
                    .normalize().multiply(0.2);
            toward.setY(0.15);
            entity.setVelocity(toward);
        }

        m.pendingOfferItem = entity;
        m.pendingOfferToken = chosen.symbol();
        m.pendingOfferAmount = amount;
        m.mockBalances.merge(chosen.symbol(), BigDecimal.valueOf(amount), BigDecimal::subtract);
        updateHologram(m);

        m.currentLocation.getWorld().playSound(m.currentLocation, Sound.ENTITY_ITEM_PICKUP, 0.4f, 0.7f);
    }

    private void consumeOffer(CrowdMember consumer, CrowdMember giver) {
        if (giver.pendingOfferItem == null || giver.pendingOfferToken == null) return;
        Item item = giver.pendingOfferItem;
        String token = giver.pendingOfferToken;
        int amount = giver.pendingOfferAmount;

        consumer.mockBalances.merge(token, BigDecimal.valueOf(amount), BigDecimal::add);
        updateHologram(consumer);

        Location at = consumer.currentLocation.clone().add(0, 1.0, 0);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.3, 0.3, 0.3);
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);

        if (item.isValid()) item.remove();
        giver.pendingOfferItem = null;
        giver.pendingOfferToken = null;
        giver.pendingOfferAmount = 0;
    }

    private void abandonTrade(CrowdMember m) {
        if (m.pendingOfferItem != null) {
            if (m.pendingOfferToken != null) {
                m.mockBalances.merge(m.pendingOfferToken,
                        BigDecimal.valueOf(m.pendingOfferAmount), BigDecimal::add);
                updateHologram(m);
            }
            if (m.pendingOfferItem.isValid()) m.pendingOfferItem.remove();
            m.pendingOfferItem = null;
            m.pendingOfferToken = null;
            m.pendingOfferAmount = 0;
        }
    }

    // ---------- movement ----------

    private void stepToward(CrowdMember m, Location target) {
        Vector delta = target.toVector().subtract(m.currentLocation.toVector());
        double distSq = delta.lengthSquared();
        if (distSq < ARRIVAL_THRESHOLD_SQ) return;
        double dist = Math.sqrt(distSq);
        double step = Math.min(STEP_DISTANCE, dist);
        Vector unit = delta.multiply(step / dist);
        World world = m.currentLocation.getWorld();

        double nextX = m.currentLocation.getX() + unit.getX();
        double nextZ = m.currentLocation.getZ() + unit.getZ();
        double nextY = world.getHighestBlockYAt((int) Math.floor(nextX), (int) Math.floor(nextZ)) + 1.0;

        // Face direction of travel.
        float yaw = (float) Math.toDegrees(Math.atan2(-unit.getX(), unit.getZ()));
        Location next = new Location(world, nextX, nextY, nextZ, yaw, 0f);
        m.currentLocation = next;
        m.npc.getData().setLocation(next);
        m.npc.moveForAll();
    }

    private void lookToward(CrowdMember m, Location target) {
        double dx = target.getX() - m.currentLocation.getX();
        double dz = target.getZ() - m.currentLocation.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (Math.abs(m.currentLocation.getYaw() - yaw) < 1.0f) return;
        m.currentLocation.setYaw(yaw);
        m.npc.getData().setLocation(m.currentLocation);
        m.npc.updateForAll();
    }

    private void stepHologram(CrowdMember m) {
        if (m.hologram != null) {
            m.hologram.setLocation(slotLocation(m.currentLocation));
        }
    }

    /** Position the balance panel just above the NPC's head. */
    private static Location slotLocation(Location loc) {
        return new Location(loc.getWorld(),
                loc.getX(),
                loc.getY() + 2.4,
                loc.getZ());
    }

    // ---------- helpers ----------

    private void seedMockBalances(CrowdMember m, ThreadLocalRandom rng) {
        for (Token t : tokens.all()) {
            m.mockBalances.put(t.symbol(), BigDecimal.valueOf(50 + rng.nextInt(450)));
        }
    }

    private void updateHologram(CrowdMember m) {
        if (m.hologram == null) return;
        m.hologram.update(tokens.all(), m.mockBalances, null);
    }

    private Location randomNearby(Location center, double minDist, double maxDist, ThreadLocalRandom rng) {
        World world = center.getWorld();
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
