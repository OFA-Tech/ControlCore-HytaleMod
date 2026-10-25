package net.ofatech.controlcore.domain.ports.game;

import java.util.List;
import net.ofatech.controlcore.domain.model.serverconfig.ConfigEntry;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public interface Worlds {

    String SPEED = "SpeedPercent";
    String JUMP = "JumpPercent";

    Worlds NONE = new Worlds() {
        @Override
        public List<String> names() {
            return List.of();
        }

        @Override
        public List<ConfigEntry> entries(String world) {
            return List.of();
        }

        @Override
        public Result<ConfigEntry> write(String world, String path, String value) {
            return Result.failure(Error.notFound("Worlds.Unknown", world));
        }

        @Override
        public int players(String world) {
            return 0;
        }

        @Override
        public boolean isPaused(String world) {
            return false;
        }

        @Override
        public void setPaused(String world, boolean paused) {}

        @Override
        public void applySaved() {}
    };

    List<String> names();

    List<ConfigEntry> entries(String world);

    Result<ConfigEntry> write(String world, String path, String value);

    int players(String world);

    boolean isPaused(String world);

    void setPaused(String world, boolean paused);

    void applySaved();
}
