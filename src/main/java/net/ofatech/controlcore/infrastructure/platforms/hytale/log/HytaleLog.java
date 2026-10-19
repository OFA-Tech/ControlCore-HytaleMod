package net.ofatech.controlcore.infrastructure.platforms.hytale.log;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleLog implements Log {

    private final HytaleLogger logger;

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
    }

    @Override
    public void error(String message, Throwable cause) {
        logger.at(Level.SEVERE).withCause(cause).log(message);
    }
}
