package net.ofatech.controlcore.domain.ports.system;

public interface Log {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);
}
