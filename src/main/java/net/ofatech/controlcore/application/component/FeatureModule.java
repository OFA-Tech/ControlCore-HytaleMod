package net.ofatech.controlcore.application.component;

/**
 * A feature that needs lifecycle hooks rather than a single command or handler. Annotate
 * implementations with {@code @AutoService(FeatureModule.class)}.
 */
public interface FeatureModule {

    default String name() {
        return getClass().getSimpleName();
    }

    default boolean isEnabled(ModuleContext context) {
        return true;
    }

    default void setup(ModuleContext context) {}

    /** Runs after other mods' capabilities are bound. */
    default void start(ModuleContext context) {}

    default void shutdown() {}
}
