package net.ofatech.controlcore.domain.ports.platform;

import java.util.function.Supplier;

/** What a {@link ModPlatform} can do while binding. */
public interface CapabilityBinder {

    /** Offers a capability implemented with the other mod's API. */
    <T> void provide(Class<T> capability, Supplier<? extends T> factory);

    /** Resolves one of our own services, for example to expose it inside the other mod. */
    <T> T service(Class<T> type);

    /** Runs when the mod platform is released at shutdown. */
    void onRelease(Runnable action);
}
