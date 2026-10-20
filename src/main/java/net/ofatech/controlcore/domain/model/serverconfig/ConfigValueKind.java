package net.ofatech.controlcore.domain.model.serverconfig;

/** How the value of a {@link ConfigEntry} is written as text. */
public enum ConfigValueKind {
    TEXT,
    INTEGER,
    /** {@code true} or {@code false}. */
    BOOLEAN,
    /** One of {@link ConfigEntry#options()}. */
    CHOICE,
    /** A duration as a whole number of seconds. */
    SECONDS,
    /** A short read-only description of a structured value (storage provider, list of mods). */
    SUMMARY
}
