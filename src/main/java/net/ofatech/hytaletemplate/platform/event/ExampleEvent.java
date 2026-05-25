package net.ofatech.hytaletemplate.platform.event;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import net.ofatech.hytaletemplate.TemplatePlugin;
import net.ofatech.hytaletemplate.core.domain.interfaces.IModEvent;

public class ExampleEvent implements IModEvent {

    @Override
    public void register(TemplatePlugin plugin) {
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Message.raw("Welcome " + player.getDisplayName()));
    }

}
