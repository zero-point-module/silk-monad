package com.silkmonad.chat;

import com.silkmonad.profile.PlayerProfile;
import com.silkmonad.profile.PlayerProfileStore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatFormatter implements Listener {

    private final PlayerProfileStore profiles;

    public ChatFormatter(PlayerProfileStore profiles) {
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        PlayerProfile profile = profiles.get(speaker.getUniqueId());
        TextColor color = profile.chatColor() != null
                ? profile.chatColor()
                : ChatColorPalette.forUuid(speaker.getUniqueId());

        event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty()
                .append(Component.text(speaker.getName(), color, TextDecoration.BOLD))
                .append(Component.text(" › ", NamedTextColor.GRAY))
                .append(Component.text().color(NamedTextColor.WHITE).append(message).build()));
    }
}
