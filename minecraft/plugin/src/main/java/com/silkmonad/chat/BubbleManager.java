package com.silkmonad.chat;

import com.silkmonad.SilkMonadPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * Floating chat bubbles to the upper-left of each player. Newest sits on top,
 * older slides below. Stack heights are calculated from each bubble's line
 * count so multi-line messages don't overlap the bubble above them.
 */
public final class BubbleManager {

    private static final long LIFETIME_MS = 10_000L;
    private static final int MAX_BUBBLES = 2;
    private static final long TICK_INTERVAL = 4L; // 5x/sec

    /** Horizontal distance from the player to the bubble column. */
    private static final double LEFT_DIST = 2.0;
    /** Y of the BOTTOM bubble (oldest) — anchored just above the player's head. */
    private static final double BASE_Y = 2.0;
    /** Approximate vertical space taken by one rendered line of TextDisplay text. */
    private static final double LINE_HEIGHT = 0.27;
    /** Gap between two stacked bubbles. */
    private static final double GAP = 0.05;
    /** Same line width we configure on the TextDisplay. */
    private static final int LINE_WIDTH_PX = 180;
    /** Approximate Minecraft default-font glyph width in pixels. */
    private static final double AVG_CHAR_PX = 6.0;

    /** Custom font defined in the resource pack — assets/silk/font/default.json. */
    private static final Key SILK_FONT = Key.key("silk", "default");
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    /** U+E100 = panel.png glyph; U+E101 = negative-advance spacer that rewinds the cursor. */
    private static final Component PANEL_PREFIX =
            Component.text("").font(SILK_FONT);

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
        // Trim oldest to make room.
        while (dq.size() >= MAX_BUBBLES) {
            Bubble oldest = dq.pollLast();
            if (oldest != null) oldest.remove();
        }
        int lineCount = estimateLineCount(message);
        Location loc = stackBase(speaker); // arbitrary initial location; tick repositions
        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.setBillboard(Billboard.CENTER);
        td.setSeeThrough(true);
        td.setShadowed(false);
        td.setPersistent(false);
        td.setLineWidth(LINE_WIDTH_PX);
        // Panel glyph (via PANEL_PREFIX) replaces the default backdrop.
        td.setDefaultBackground(false);
        td.setTeleportDuration((int) TICK_INTERVAL);
        td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f()));
        // Force the message body to render in Minecraft's default font so it
        // doesn't accidentally inherit the silk font from the panel prefix.
        td.text(PANEL_PREFIX.append(message.font(DEFAULT_FONT)));
        dq.push(new Bubble(td, System.currentTimeMillis(), lineCount));
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
            // Prune expired (oldest sits at tail).
            while (!dq.isEmpty() && (now - dq.peekLast().createdAtMillis) > LIFETIME_MS) {
                Bubble expired = dq.pollLast();
                expired.remove();
            }
            // Position oldest → newest, accumulating y by each bubble's line count.
            Location base = stackBase(p);
            double y = base.getY();
            Iterator<Bubble> rev = dq.descendingIterator(); // oldest first
            while (rev.hasNext()) {
                Bubble b = rev.next();
                if (!b.display.isDead()) {
                    b.display.teleport(new Location(base.getWorld(),
                            base.getX(), y, base.getZ()));
                }
                y += b.lineCount * LINE_HEIGHT + GAP;
            }
        }
    }

    /** Base of the bubble column — 2 blocks to the player's left, just above head. */
    private static Location stackBase(Player p) {
        Location feet = p.getLocation();
        double yawRad = Math.toRadians(feet.getYaw());
        // Right vector is (cos, sin); left is the negation.
        double lx = -Math.cos(yawRad);
        double lz = -Math.sin(yawRad);
        return new Location(feet.getWorld(),
                feet.getX() + lx * LEFT_DIST,
                feet.getY() + BASE_Y,
                feet.getZ() + lz * LEFT_DIST);
    }

    /** Rough rendered-line count for a Component: explicit newlines + wrap. */
    private static int estimateLineCount(Component message) {
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        int total = 0;
        for (String segment : plain.split("\n", -1)) {
            int chars = Math.max(1, segment.length());
            int wrapped = (int) Math.ceil((chars * AVG_CHAR_PX) / LINE_WIDTH_PX);
            total += Math.max(1, wrapped);
        }
        return Math.max(1, total);
    }
}
