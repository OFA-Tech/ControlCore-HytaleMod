package net.ofatech.controlcore.platform.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.server.core.entity.entities.Player;
import net.ofatech.controlcore.ControlCore;
import net.ofatech.controlcore.core.domain.interfaces.IModCommand;
import net.ofatech.controlcore.core.domain.models.ControlPanelRequest;
import net.ofatech.controlcore.platform.hyui.page.ControlPanel;

public class ControlCoreUiCommand extends AbstractCommand implements IModCommand {
    public ControlCoreUiCommand() {
        super("open-admin-config", "A command to open the ControlCore admin configuration panel", false);
    }

    @Override
    public void register(ControlCore plugin) {
        plugin.getCommandRegistry().registerCommand(this);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        var sender = context.sender();
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(null);
        }

        return ControlPanel.open(
            player,
            new ControlPanelRequest(true, true, true));
    }
}
