package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * Runs commands through Hytale's {@link CommandManager} as the player, so their permissions apply. Every run gets
 * its own virtual thread: key presses arrive on network threads, which must not wait for a command.
 */
public final class HytaleCommandRunner implements CommandRunner, AutoCloseable {

    private final Log log;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public HytaleCommandRunner(Log log) {
        this.log = log;
    }

    @Override
    public void runAs(PlayerId player, String command) {
        runInBackground("command /" + command, () -> {
            PlayerRef sender = Universe.get().getPlayer(player.value());
            if (sender == null) {
                return;
            }
            CommandManager.get().handleCommand(sender, command).join();
        });
    }

    @Override
    public void runAsServer(String command) {
        runInBackground(
                "server command /" + command,
                () -> CommandManager.get()
                        .handleCommand(ConsoleSender.INSTANCE, command)
                        .join());
    }

    @Override
    public void runInBackground(String description, Runnable work) {
        try {
            threads.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException e) {
                    log.error("Failed to run " + description, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Not running " + description + ": the server is shutting down");
        }
    }

    @Override
    public void close() {
        threads.shutdown();
    }
}
