package net.ofatech.controlcore.fakes;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.ServerSettings;
import net.ofatech.controlcore.domain.ports.game.ServerConfiguration;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/** In-memory server config; remembers every write and can be told to reject them. */
public final class FakeServerConfiguration implements ServerConfiguration {

    public static final ServerSettings DEFAULTS =
            new ServerSettings("Hytale Server", "", false, 100, 32, Duration.ofSeconds(60));

    public volatile ServerSettings current;
    public volatile boolean failWrites;
    public final List<ServerSettings> writes = new CopyOnWriteArrayList<>();

    public FakeServerConfiguration(ServerSettings current) {
        this.current = current;
    }

    @Override
    public ServerSettings read() {
        return current;
    }

    @Override
    public ServerSettings defaults() {
        return DEFAULTS;
    }

    @Override
    public Result<ServerSettings> write(ServerSettings settings) {
        if (failWrites) {
            return Result.failure(Error.failure("ServerConfig.WriteFailed", "The server rejected the change."));
        }
        writes.add(settings);
        // The password is not editable: keep the current flag, like the real server.
        current = new ServerSettings(
                settings.serverName(),
                settings.motd(),
                current.passwordProtected(),
                settings.maxPlayers(),
                settings.maxViewRadius(),
                settings.playTimeout());
        return Result.success(current);
    }
}
