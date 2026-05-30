package com.silkmonad.hologram;

import com.silkmonad.chain.Token;
import com.silkmonad.merchant.Merchant;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Display.Billboard;
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

    /** Free-floating — caller repositions via {@link #setLocation}. */
    public BalanceHologram(Location at, float yOffset) {
        this.display = makeDisplay(at.clone().add(0, yOffset, 0), 0f);
    }

    private static TextDisplay makeDisplay(Location at, float yOffset) {
        TextDisplay td = (TextDisplay) at.getWorld().spawnEntity(at, org.bukkit.entity.EntityType.TEXT_DISPLAY);
        td.setBillboard(Billboard.CENTER);
        td.setSeeThrough(true);
        td.setShadowed(false);
        td.setPersistent(false);
        td.setDefaultBackground(true);
        td.setTeleportDuration(4);
        td.setTransformation(new Transformation(
                new Vector3f(0f, yOffset, 0f),
                new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f),
                new AxisAngle4f()));
        return td;
    }

    /** For free-floating mode: re-teleport to follow some external position. */
    public void setLocation(Location loc) {
        if (!display.isDead()) display.teleport(loc);
    }

    public void update(List<Token> tokens, Map<String, BigDecimal> balances, @Nullable Merchant merchant) {
        Component text = Component.empty();
        boolean first = true;
        if (merchant != null) {
            TextColor color = merchant.color() != null ? merchant.color() : NamedTextColor.WHITE;
            text = text.append(Component.text(merchant.name(), color, TextDecoration.BOLD));
            first = false;
        }
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
