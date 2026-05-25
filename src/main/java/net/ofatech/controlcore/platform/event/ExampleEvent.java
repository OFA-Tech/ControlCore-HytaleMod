package net.ofatech.controlcore.platform.event;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import net.ofatech.controlcore.ControlCore;
import net.ofatech.controlcore.core.domain.interfaces.IModEvent;

public class ExampleEvent implements IModEvent {

    @Override
    public void register(ControlCore plugin) {
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Message.raw("Welcome " + player.getDisplayName()));
    }

}
