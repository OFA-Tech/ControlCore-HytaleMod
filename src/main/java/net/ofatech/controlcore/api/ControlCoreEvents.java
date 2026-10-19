package net.ofatech.controlcore.api;

import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.DomainEvent;
import net.ofatech.controlcore.domain.event.Subscription;

/**
 * Events of this mod, for example {@code GreetingSending} (cancellable) and {@code GreetingSent}.
 * Listeners run synchronously on the thread that published the event.
 */
public interface ControlCoreEvents {

    /** Listens to {@code type} and its subtypes until the returned subscription is closed. */
    <E extends DomainEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener);
}
