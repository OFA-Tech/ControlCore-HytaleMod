package net.ofatech.controlcore.domain.event;

/**
 * Handle of an event listener, returned when you subscribe, for example by
 * {@link net.ofatech.controlcore.api.ControlCoreEvents#subscribe(Class, java.util.function.Consumer)}; closing it
 * stops delivery.
 */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    /**
     * Stops delivering events to the listener. Unlike {@link AutoCloseable#close()}, it throws no checked
     * exception.
     */
    @Override
    void close();
}
