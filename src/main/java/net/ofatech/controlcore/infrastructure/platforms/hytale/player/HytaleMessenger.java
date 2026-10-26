package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Messages;

public final class HytaleMessenger implements Messenger {

    @Override
    public void send(PlayerId player, String text) {
        PlayerRef ref = Universe.get().getPlayer(player.value());
        if (ref != null) {
            ref.sendMessage(Messages.text(text));
        }
    }

    @Override
    public void send(PlayerId player, String text, String color) {
        PlayerRef ref = Universe.get().getPlayer(player.value());
        if (ref != null) {
            ref.sendMessage(com.hypixel.hytale.server.core.Message.raw(text).color(color));
        }
    }

    @Override
    public void broadcast(String text) {
        Universe.get().sendMessage(Messages.text(text));
    }

    @Override
    public void title(PlayerId player, String title, String subtitle) {
        PlayerRef ref = Universe.get().getPlayer(player.value());
        if (ref != null) {
            EventTitleUtil.showEventTitleToPlayer(
                    ref, Message.raw(title), Message.raw(subtitle), EventTitleStyle.Major);
        }
    }

    @Override
    public void titleAll(String title, String subtitle) {
        for (PlayerRef ref : Universe.get().getPlayers()) {
            EventTitleUtil.showEventTitleToPlayer(
                    ref, Message.raw(title), Message.raw(subtitle), EventTitleStyle.Major);
        }
    }
}
