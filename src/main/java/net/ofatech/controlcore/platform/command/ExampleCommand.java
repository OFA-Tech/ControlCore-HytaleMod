package net.ofatech.controlcore.platform.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

import net.ofatech.controlcore.ControlCore;
import net.ofatech.controlcore.core.domain.interfaces.IModCommand;

public class ExampleCommand extends AbstractCommand implements IModCommand {

    public ExampleCommand() {
        super("example", "An example command");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw("Hello from ExampleCommand!"));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void register(ControlCore plugin) {
        plugin.getCommandRegistry().registerCommand(this);
    }

}
