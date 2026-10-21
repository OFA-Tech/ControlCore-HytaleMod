package net.ofatech.controlcore.application.component;

public interface FeatureModule {

    default String name() {
        return getClass().getSimpleName();
    }

    default boolean isEnabled(ModuleContext context) {
        return true;
    }

    default void setup(ModuleContext context) {}

    default void start(ModuleContext context) {}

    default void shutdown() {}
}
