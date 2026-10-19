package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class RecordingLog implements Log {

    public final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void debug(String message) {
        lines.add("DEBUG " + message);
    }

    @Override
    public void info(String message) {
        lines.add("INFO " + message);
    }

    @Override
    public void warn(String message) {
        lines.add("WARN " + message);
    }

    @Override
    public void error(String message, Throwable cause) {
        lines.add("ERROR " + message + ": " + cause);
    }

    public boolean contains(String text) {
        return lines.stream().anyMatch(line -> line.contains(text));
    }
}
