package net.ofatech.controlcore.domain.ports.platform;

/**
 * Descriptor of another mod we can integrate with. Implementations must not reference the other
 * mod's classes directly; {@link #bind} is only called after the mod was found to be loaded.
 */
public interface ModPlatform {

    /** Plugin identifier of the other mod, {@code "Group:Name"}. */
    String modId();

    void bind(CapabilityBinder binder);
}
