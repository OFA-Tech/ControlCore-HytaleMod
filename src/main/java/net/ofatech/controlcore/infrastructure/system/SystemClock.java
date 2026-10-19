package net.ofatech.controlcore.infrastructure.system;

import java.time.Instant;
import net.ofatech.controlcore.domain.ports.system.Clock;

public final class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
