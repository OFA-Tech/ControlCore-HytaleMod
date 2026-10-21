package net.ofatech.controlcore.domain.ports.platform;

import java.util.function.Supplier;

public interface CapabilityBinder {

    <T> void provide(Class<T> capability, Supplier<? extends T> factory);

    <T> T service(Class<T> type);

    void onRelease(Runnable action);
}
