package net.ofatech.controlcore.domain.ports.platform;

import net.ofatech.controlcore.domain.ports.event.EventPublisher;

/** The host the mod runs in. Its implementation translates the host's API into our ports. */
public interface Platform {

    String name();

    /** Binds every port the host provides (players, commands, scheduler, settings, log, ...). */
    void bind(PortBinder binder);

    /** Starts forwarding host events, translated to our domain events, to {@code publisher}. */
    void connect(EventPublisher publisher);
}
