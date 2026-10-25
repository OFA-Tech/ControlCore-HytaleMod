package net.ofatech.controlcore.domain.ports.system;

import java.time.Instant;
import java.util.List;

public interface RecentProblems {

    record Problem(Instant at, boolean error, String message) {}

    List<Problem> recent();
}
