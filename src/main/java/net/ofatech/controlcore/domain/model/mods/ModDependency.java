package net.ofatech.controlcore.domain.model.mods;

import net.ofatech.controlcore.domain.guard.Guard;

public record ModDependency(String id, String range, boolean optional, Status status) {

    public enum Status {
        OK,
        MISSING,
        WRONG_VERSION,
        OFF
    }

    public ModDependency {
        Guard.notBlank(id, "id");
        range = range == null || range.isBlank() || range.strip().equals("*") ? "" : range.strip();
        Guard.notNull(status, "status");
    }

    public ModDependency withStatus(Status changed) {
        return new ModDependency(id, range, optional, changed);
    }

    public boolean problem() {
        return !optional && status != Status.OK;
    }

    public boolean hytale() {
        return id.regionMatches(true, 0, "Hytale:", 0, 7);
    }
}
