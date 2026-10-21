package net.ofatech.controlcore.api;

import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.DomainEvent;
import net.ofatech.controlcore.domain.event.Subscription;

/**
 * Events of this mod: {@link net.ofatech.controlcore.domain.event.PlayerJoined},
 * {@link net.ofatech.controlcore.domain.event.PlayerLeft},
 * {@link net.ofatech.controlcore.domain.event.SettingChanged} and
 * {@link net.ofatech.controlcore.domain.event.ServerConfigChanged}. Listeners run synchronously on the thread that
 * published the event; an exception from a listener is logged and does not stop the others. Get it with
 * {@link ControlCoreApi#events()}.
 */
public interface ControlCoreEvents {

    /**
     * Listens to {@code type} and its subtypes until the returned subscription is closed.
     *
     * @param <E> the event type
     * @param type the event class, for example {@code SettingChanged.class}; {@code DomainEvent.class} gets every
     *     event
     * @param listener called with each event of that type
     * @return the subscription; close it to stop listening
     */
    <E extends DomainEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener);
}
