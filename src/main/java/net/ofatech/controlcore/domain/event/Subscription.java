package net.ofatech.controlcore.domain.event;

/** Handle of an event listener; closing it stops delivery. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    @Override
    void close();
}
