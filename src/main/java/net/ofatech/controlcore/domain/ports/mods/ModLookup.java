package net.ofatech.controlcore.domain.ports.mods;

public interface ModLookup {

    /** Whether the mod {@code "Group:Name"} is installed and not failed or disabled. */
    boolean isLoaded(String modId);
}
