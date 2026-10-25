package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.system.ServerHealth;

public final class HytaleServerHealth implements ServerHealth {

    private static final long TEN_SECONDS = 10_000_000_000L;

    @Override
    public Machine machine() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double process = -1;
        double machine = -1;
        if (os instanceof com.sun.management.OperatingSystemMXBean detailed) {
            process = detailed.getProcessCpuLoad();
            machine = detailed.getCpuLoad();
        }
        return new Machine(
                System.getProperty("java.version", "?"),
                used / (1024 * 1024),
                runtime.maxMemory() / (1024 * 1024),
                os.getAvailableProcessors(),
                process,
                machine);
    }

    @Override
    public String gameVersion() {
        try {
            String version = ManifestUtil.getVersion();
            return version == null || version.isBlank() ? "?" : version;
        } catch (RuntimeException e) {
            return "?";
        }
    }

    @Override
    public Optional<Instant> bootedAt() {
        try {
            return Optional.ofNullable(HytaleServer.get().getBoot());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public int playersOnline() {
        return Universe.get().getPlayerCount();
    }

    @Override
    public int maxPlayers() {
        return HytaleServer.get().getConfig().getMaxPlayers();
    }

    @Override
    public List<WorldHealth> worlds() {
        List<WorldHealth> worlds = new ArrayList<>();
        for (World world : Universe.get().getWorlds().values()) {
            worlds.add(new WorldHealth(
                    world.getName(),
                    world.getPlayerCount(),
                    world.getChunkStore().getLoadedChunksCount(),
                    world.getTps(),
                    tickMillis(world),
                    world.isTicking(),
                    world.isPaused()));
        }
        worlds.sort(Comparator.comparing(WorldHealth::name));
        return worlds;
    }

    private static double tickMillis(World world) {
        try {
            HistoricMetric metric = world.getBufferedTickLengthMetricSet();
            long[] periods = metric.getPeriodsNanos();
            if (periods.length == 0) {
                return -1;
            }
            int best = 0;
            for (int index = 1; index < periods.length; index++) {
                if (Math.abs(periods[index] - TEN_SECONDS) < Math.abs(periods[best] - TEN_SECONDS)) {
                    best = index;
                }
            }
            double average = metric.getAverage(best);
            return Double.isFinite(average) && average > 0 ? average / 1_000_000.0 : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
