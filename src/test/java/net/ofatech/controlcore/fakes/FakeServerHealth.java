package net.ofatech.controlcore.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;

public final class FakeServerHealth implements ServerHealth {

    public final List<WorldHealth> worlds = new ArrayList<>();
    public Optional<Instant> booted = Optional.of(Instant.parse("2026-10-18T10:00:00Z"));
    public int players;

    @Override
    public Machine machine() {
        return new Machine("25", 1024, 4096, 8, 0.1, 0.2);
    }

    @Override
    public String gameVersion() {
        return "0.7.4";
    }

    @Override
    public Optional<Instant> bootedAt() {
        return booted;
    }

    @Override
    public int playersOnline() {
        return players;
    }

    @Override
    public int maxPlayers() {
        return 100;
    }

    @Override
    public List<WorldHealth> worlds() {
        return worlds;
    }
}
