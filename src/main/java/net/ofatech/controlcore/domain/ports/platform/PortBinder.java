package net.ofatech.controlcore.domain.ports.platform;

@FunctionalInterface
public interface PortBinder {

    <T> void bind(Class<T> port, T implementation);
}
