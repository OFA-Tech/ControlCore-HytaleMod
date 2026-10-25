package net.ofatech.controlcore.domain.ports.system;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ServerHealth {

    record WorldHealth(
            String name, int players, int chunks, int targetTps, double tickMillis, boolean ticking, boolean paused) {}

    record Machine(String java, long usedMb, long maxMb, int cores, double processCpu, double machineCpu) {}

    Machine machine();

    String gameVersion();

    Optional<Instant> bootedAt();

    int playersOnline();

    int maxPlayers();

    List<WorldHealth> worlds();
}
