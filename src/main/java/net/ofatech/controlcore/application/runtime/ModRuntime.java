package net.ofatech.controlcore.application.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.ControlCoreApiProvider;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.application.component.ModTask;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.application.component.TaskSchedule;
import net.ofatech.controlcore.application.event.DomainEventBus;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.text.CommandTexts;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.event.DomainEvent;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.ports.command.CommandRegistrar;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.game.Scheduler;
import net.ofatech.controlcore.domain.ports.game.Scheduler.ScheduledTask;
import net.ofatech.controlcore.domain.ports.mods.ModLookup;
import net.ofatech.controlcore.domain.ports.platform.Capabilities;
import net.ofatech.controlcore.domain.ports.platform.CapabilityBinder;
import net.ofatech.controlcore.domain.ports.platform.ModPlatform;
import net.ofatech.controlcore.domain.ports.platform.Platform;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class ModRuntime {

    private final Platform platform;
    private final ComponentIndex index;
    private final Injector injector;
    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<FeatureModule> modules = new ArrayList<>();
    private final List<ScheduledTask> scheduled = new ArrayList<>();
    private final List<ModTask> shutdownTasks = new ArrayList<>();
    private final List<Runnable> releaseActions = new ArrayList<>();

    private Log log;
    private DomainEventBus bus;
    private ModuleContext context;
    private ControlCoreApi api;

    public ModRuntime(Platform platform, ClassLoader classLoader) {
        this.platform = platform;
        this.index = new ComponentIndex(classLoader);
        this.injector = new Injector(index);
    }

    public void setup() {
        platform.bind(injector::bind);
        log = injector.get(Log.class);
        injector.bind(Capabilities.class, capabilities);
        bus = injector.get(DomainEventBus.class);
        injector.bind(EventPublisher.class, bus);
        injector.bind(ControlCoreEvents.class, bus);
        context = new ModuleContext(
                injector.get(SettingsProvider.class),
                capabilities,
                injector.get(CommandRegistrar.class),
                bus,
                injector.get(Scheduler.class),
                log);
        injector.bind(ModuleContext.class, context);

        List<String> failures = new ArrayList<>();
        CommandRegistrar commands = context.commands();
        Texts texts = injector.get(Texts.class);
        each(
                ModCommand.class,
                failures,
                command -> commands.register(
                        CommandTexts.spec(command.spec(), texts),
                        CommandTexts.handler(
                                command.spec(),
                                commandContext -> ChangeContext.fromCommand(
                                        commandContext.sender(), () -> command.execute(commandContext)),
                                texts)));
        each(eventHandlerType(), failures, handler -> subscriptions.add(subscribe(handler)));
        each(FeatureModule.class, failures, module -> {
            if (module.isEnabled(context)) {
                module.setup(context);
                modules.add(module);
            } else {
                log.info("Module " + module.name() + " is disabled");
            }
        });
        if (!failures.isEmpty()) {
            failures.forEach(failure -> log.warn(failure));
            throw new IllegalStateException(
                    failures.size() + " component(s) could not be set up:\n- " + String.join("\n- ", failures));
        }

        platform.connect(bus);
        api = injector.get(ControlCoreApi.class);
        ControlCoreApiProvider.register(api);
        log.info("Set up on " + platform.name() + ": " + modules.size() + " module(s), " + subscriptions.size()
                + " event handler(s)");
    }

    public void start() {
        ModLookup mods = injector.get(ModLookup.class);
        for (Class<? extends ModPlatform> type : index.providers(ModPlatform.class)) {
            bindModPlatform(type, mods);
        }
        for (FeatureModule module : modules) {
            guarded("start module " + module.name(), () -> module.start(context));
        }
        Scheduler scheduler = context.scheduler();
        for (Class<? extends ModTask> type : index.providers(ModTask.class)) {
            guarded("schedule task " + type.getSimpleName(), () -> schedule(injector.get(type), scheduler));
        }
    }

    public void shutdown() {
        if (api != null) {
            ControlCoreApiProvider.unregister(api);
            api = null;
        }
        scheduled.forEach(ScheduledTask::cancel);
        scheduled.clear();
        shutdownTasks.forEach(task -> guarded("run task " + task.getClass().getSimpleName(), task::run));
        shutdownTasks.clear();
        reversed(modules).forEach(module -> guarded("shut down module " + module.name(), module::shutdown));
        modules.clear();
        reversed(releaseActions).forEach(action -> guarded("release a mod platform", action));
        releaseActions.clear();
        capabilities.clear();
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        if (bus != null) {
            bus.clear();
        }
    }

    private <T> void each(Class<T> type, List<String> failures, Consumer<T> action) {
        for (Class<? extends T> implementation : index.providers(type)) {
            try {
                action.accept(injector.get(implementation));
            } catch (RuntimeException e) {
                failures.add(implementation.getName() + ": " + e.getMessage());
                log.error("Could not set up " + implementation.getName(), e);
            }
        }
    }

    private Subscription subscribe(ModEventHandler<?> handler) {
        return subscribeTyped(handler);
    }

    private <E extends DomainEvent> Subscription subscribeTyped(ModEventHandler<E> handler) {
        return bus.subscribe(handler.eventType(), handler::handle);
    }

    Capabilities capabilities() {
        return capabilities;
    }

    private void bindModPlatform(Class<? extends ModPlatform> type, ModLookup mods) {
        ModPlatform descriptor;
        try {
            descriptor = injector.get(type);
        } catch (RuntimeException e) {
            log.error("Could not create mod platform " + type.getName(), e);
            return;
        }
        if (!mods.isLoaded(descriptor.modId())) {
            log.debug(descriptor.modId() + " is not installed; " + type.getSimpleName() + " stays inactive");
            return;
        }
        StagedBinding binding = new StagedBinding();
        try {
            descriptor.bind(binding);
            binding.commit();
            log.info("Integrated with " + descriptor.modId());
        } catch (LinkageError | RuntimeException e) {
            binding.rollback();
            log.error("Integration with " + descriptor.modId() + " failed; it stays inactive", e);
        }
    }

    private void schedule(ModTask task, Scheduler scheduler) {
        Runnable work = () -> guarded("run task " + task.getClass().getSimpleName(), task::run);
        TaskSchedule plan = task.schedule();
        scheduled.add(plan.period()
                .map(period -> scheduler.schedule(plan.initialDelay(), period, work))
                .orElseGet(() -> scheduler.runLater(plan.initialDelay(), work)));
        if (task.runOnShutdown()) {
            shutdownTasks.add(task);
        }
    }

    private void guarded(String action, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("Failed to " + action, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<ModEventHandler<?>> eventHandlerType() {
        return (Class<ModEventHandler<?>>) (Class<?>) ModEventHandler.class;
    }

    private static <T> List<T> reversed(List<T> items) {
        List<T> copy = new ArrayList<>(items);
        Collections.reverse(copy);
        return copy;
    }

    private final class StagedBinding implements CapabilityBinder {

        private final Map<Class<?>, Object> provided = new LinkedHashMap<>();
        private final List<Runnable> releases = new ArrayList<>();

        @Override
        public <T> void provide(Class<T> capability, Supplier<? extends T> factory) {
            provided.put(capability, capability.cast(factory.get()));
        }

        @Override
        public <T> T service(Class<T> type) {
            return injector.get(type);
        }

        @Override
        public void onRelease(Runnable action) {
            releases.add(action);
        }

        void commit() {
            provided.forEach((capability, implementation) -> {
                if (!capabilities.offer(capability, implementation)) {
                    log.warn(capability.getSimpleName() + " is already provided by another mod; keeping the first");
                }
            });
            releaseActions.addAll(releases);
        }

        void rollback() {
            reversed(releases).forEach(action -> guarded("roll back a mod platform", action));
        }
    }
}
