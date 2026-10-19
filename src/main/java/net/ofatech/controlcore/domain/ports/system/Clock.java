package net.ofatech.controlcore.domain.ports.system;

import java.time.Instant;

@FunctionalInterface
public interface Clock {

    Instant now();
}
