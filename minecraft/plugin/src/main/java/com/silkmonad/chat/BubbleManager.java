package com.silkmonad.chat;

import com.silkmonad.SilkMonadPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Floating chat bubbles above players' heads — each message a TextDisplay
 * styled like Minecraft's nametag (dark translucent backdrop, white text),
 * positioned to the right of the player's head and following them every tick.
 *
 * Per player: up to {@link #MAX_BUBBLES} bubbles. Newest sits on top, older
 * below. Each bubble auto-expires {@link #LIFETIME_MS} ms after it was sent.
 */
public final class BubbleManager {

    private static final long LIFETIME_MS = 10_000L;
    private static final int MAX_BUBBLES = 2;
    private static final long TICK_INTERVAL = 4L; // 5x/sec — feels glued to the player
    private static final double RIGHT_DIST = 0.7;
    private static final double TOP_Y_OFFSET = 0.6;
    private static final double BOTTOM_Y_OFFSET = 0.2;

    private final SilkMonadPlugin plugin;
    private final Map<UUID, Deque<Bubble>> bubbles = new HashMap<>();
    private BukkitTask task;

    public BubbleManager(SilkMonadPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Deque<Bubble> dq : bubbles.values()) {
            for (Bubble b : dq) b.remove();
        }
        bubbles.clear();
    }

    /** Call on the main thread when a player sends a chat message. */
    public void onChat(Player speaker, Component message) {
        Deque<Bubble> dq = bubbles.computeIfAbsent(speaker.getUniqueId(), k -> new ArrayDeque<>());
        // Trim to keep room for the new bubble — drop the oldest.
        while (dq.size() >= MAX_BUBBLES) {
            Bubble oldest = dq.pollLast();
            if (oldest != null) oldest.remove();
        }
        Location loc = slotLocation(speaker, 0);
        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.setBillboard(Billboard.CENTER);
        td.setSeeThrough(true);
        td.setShadowed(false);
        td.setPersistent(false);
        td.setLineWidth(180);
        // Use Minecraft's native text overlay backdrop (same one tooltips and the
        // scoreboard use) instead of a flat color — feels GUI-like.
        td.setDefaultBackground(true);
        // Interpolate position changes over the tick interval so the bubble glides
        // instead of teleporting jerkily after the player.
        td.setTeleportDuration((int) TICK_INTERVAL);
        td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f()));
        td.text(message);
        dq.push(new Bubble(td, System.currentTimeMillis())); // newest at the front (top)
    }

    public void onQuit(UUID uuid) {
        Deque<Bubble> dq = bubbles.remove(uuid);
        if (dq != null) for (Bubble b : dq) b.remove();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Deque<Bubble>>> it = bubbles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<Bubble>> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            Deque<Bubble> dq = entry.getValue();
            if (p == null || !p.isOnline()) {
                for (Bubble b : dq) b.remove();
                it.remove();
                continue;
            }
            // Prune expired (oldest sits at tail — pollLast was for trimming on add;
            // since we push newest to head, the tail is the oldest).
            while (!dq.isEmpty() && (now - dq.peekLast().createdAtMillis) > LIFETIME_MS) {
                Bubble expired = dq.pollLast();
                expired.remove();
            }
            // Reposition: 0 = newest = top slot, 1 = older = bottom slot.
            int i = 0;
            for (Bubble b : dq) {
                if (!b.display.isDead()) b.display.teleport(slotLocation(p, i));
                i++;
            }
        }
    }

    /** World location for the given bubble slot, to the right of the player's head. */
    private static Location slotLocation(Player p, int slotIndex) {
        Location eye = p.getEyeLocation();
        double yawRad = Math.toRadians(eye.getYaw());
        // Minecraft yaw: 0=south. Right-of-forward = (cos(yaw), sin(yaw)) in (x,z).
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);
        double yOffset = (slotIndex == 0) ? TOP_Y_OFFSET : BOTTOM_Y_OFFSET;
        return new Location(eye.getWorld(),
                eye.getX() + rx * RIGHT_DIST,
                eye.getY() + yOffset,
                eye.getZ() + rz * RIGHT_DIST);
    }
}
