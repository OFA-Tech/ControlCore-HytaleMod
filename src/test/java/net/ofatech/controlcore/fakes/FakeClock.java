package net.ofatech.controlcore.fakes;

import java.time.Instant;
import net.ofatech.controlcore.domain.ports.system.Clock;

public final class FakeClock implements Clock {

    public Instant now = Instant.parse("2026-10-18T12:00:00Z");

    @Override
    public Instant now() {
        return now;
    }
}
