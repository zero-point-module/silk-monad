package com.silkmonad.hologram;

import com.silkmonad.chain.Token;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

public final class BalanceHologram {

    /** Custom font defined in the resource pack — assets/silk/font/default.json. */
    private static final Key SILK_FONT = Key.key("silk", "default");
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.####");

    private final TextDisplay display;

    public BalanceHologram(Player player) {
        this.display = (TextDisplay) player.getWorld().spawnEntity(
                player.getLocation(), org.bukkit.entity.EntityType.TEXT_DISPLAY);
        display.setBillboard(Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(false);
        display.setPersistent(false);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0.6f, 0f),
                new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f()));
        player.addPassenger(display);
    }

    public void update(List<Token> tokens, Map<String, BigDecimal> balances) {
        Component text = Component.empty();
        boolean first = true;
        for (Token token : tokens) {
            BigDecimal amount = balances.getOrDefault(token.symbol(), BigDecimal.ZERO);
            if (!first) text = text.append(Component.newline());
            first = false;

            TextColor color = token.color() != null ? token.color() : NamedTextColor.WHITE;

            if (token.glyph() != null && !token.glyph().isEmpty()) {
                text = text.append(Component.text(token.glyph()).font(SILK_FONT))
                        .append(Component.text(" "));
            }
            text = text
                    .append(Component.text(token.symbol(), color))
                    .append(Component.text(" "))
                    .append(Component.text(FMT.format(amount), NamedTextColor.WHITE));
        }
        display.text(text);
    }

    public void showError(String message) {
        display.text(Component.text("(" + message + ")", NamedTextColor.RED));
    }

    public void remove() {
        if (!display.isDead()) display.remove();
    }
}
