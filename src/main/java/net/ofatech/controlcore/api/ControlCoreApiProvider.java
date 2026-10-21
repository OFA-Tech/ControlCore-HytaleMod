package net.ofatech.controlcore.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the running {@link ControlCoreApi}. Registration is done by this mod's runtime only; other mods use
 * {@link ControlCoreApi#get()} or {@link ControlCoreApi#find()}.
 */
public final class ControlCoreApiProvider {

    private static final AtomicReference<ControlCoreApi> CURRENT = new AtomicReference<>();

    private ControlCoreApiProvider() {}

    static ControlCoreApi get() {
        ControlCoreApi api = CURRENT.get();
        if (api == null) {
            throw new IllegalStateException("The mod API is not available: the mod is not loaded or not set up yet");
        }
        return api;
    }

    static Optional<ControlCoreApi> find() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Internal: called by this mod at the end of setup to publish the API.
     *
     * @param api the running API
     * @throws NullPointerException if {@code api} is {@code null}
     * @throws IllegalStateException if an API is already registered
     */
    public static void register(ControlCoreApi api) {
        if (!CURRENT.compareAndSet(null, Objects.requireNonNull(api, "api"))) {
            throw new IllegalStateException("The mod API is already registered");
        }
    }

    /**
     * Internal: called by this mod at shutdown; does nothing if {@code api} is not the registered one.
     *
     * @param api the API passed to {@link #register(ControlCoreApi)}
     */
    public static void unregister(ControlCoreApi api) {
        CURRENT.compareAndSet(api, null);
    }
}
