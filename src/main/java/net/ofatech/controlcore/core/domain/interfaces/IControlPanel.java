package net.ofatech.controlcore.core.domain.interfaces;

import com.hypixel.hytale.server.core.entity.entities.Player;
import net.ofatech.controlcore.core.domain.models.ControlPanelRequest;

import java.util.concurrent.CompletableFuture;

public interface IControlPanel {
    CompletableFuture<Void> openControlPanel(Player player, ControlPanelRequest request);
}
