package net.ofatech.controlcore.application.component;

/** Delayed or repeating work. Annotate implementations with {@code @AutoService(ModTask.class)}. */
public interface ModTask {

    TaskSchedule schedule();

    void run();

    /** Whether the task runs once more when the mod shuts down. */
    default boolean runOnShutdown() {
        return false;
    }
}
