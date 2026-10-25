package net.ofatech.controlcore.infrastructure.platforms.hytale.log;

import com.hypixel.hytale.logger.HytaleLogger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.ports.system.RecentProblems;

public final class HytaleLog implements Log, RecentProblems {

    static final int REMEMBERED = 30;

    private final HytaleLogger logger;
    private final Deque<Problem> problems = new ArrayDeque<>();

    public HytaleLog(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(String message) {
        logger.at(Level.FINE).log(message);
    }

    @Override
    public void info(String message) {
        logger.at(Level.INFO).log(message);
    }

    @Override
    public void warn(String message) {
        logger.at(Level.WARNING).log(message);
        remember(false, message);
    }

    @Override
    public void error(String message, Throwable cause) {
        logger.at(Level.SEVERE).withCause(cause).log(message);
        remember(true, cause == null ? message : message + ": " + cause);
    }

    @Override
    public synchronized List<Problem> recent() {
        return List.copyOf(problems);
    }

    private synchronized void remember(boolean error, String message) {
        problems.addFirst(new Problem(Instant.now(), error, message == null ? "" : message));
        while (problems.size() > REMEMBERED) {
            problems.removeLast();
        }
    }
}
