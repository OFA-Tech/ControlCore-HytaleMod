package net.ofatech.controlcore.domain.event;

/** An event whose listeners can stop the action it announces. */
public interface CancellableEvent extends DomainEvent {

    boolean isCancelled();

    void cancel();
}
