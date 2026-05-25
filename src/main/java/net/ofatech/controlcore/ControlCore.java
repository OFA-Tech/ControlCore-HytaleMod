package net.ofatech.controlcore;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import net.ofatech.controlcore.core.domain.interfaces.IModCommand;
import net.ofatech.controlcore.core.domain.interfaces.IModEvent;
import net.ofatech.controlcore.core.domain.interfaces.IServiceCollection;
import net.ofatech.controlcore.core.domain.interfaces.IServiceProvider;
import net.ofatech.controlcore.core.service.ServiceCollection;
import net.ofatech.controlcore.platform.hyui.ControlPanel;
import net.ofatech.controlcore.platform.registry.ModComponentScanner;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.util.Set;

public class ControlCore extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private IServiceProvider serviceProvider;

    public ControlCore(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Initializing ControlCore  with Version: %s", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        configureDependencies();
        registerDynamicComponents();
    }

    private void configureDependencies() {
        IServiceCollection services = new ServiceCollection();

        // Register platform services as singletons
        services.addSingleton(ControlPanel.class, new ControlPanel());

        // Build the service provider
        serviceProvider = services.buildServiceProvider();
        LOGGER.atInfo().log("Service provider configured");
    }

    private void registerDynamicComponents() {
        Class<?> thisClass = getClass();
        Set<Class<?>> candidates = ModComponentScanner.scan(thisClass.getPackageName(), thisClass.getClassLoader());
        for (Class<?> candidate : candidates) {
            if (IModCommand.class.isAssignableFrom(candidate) && isConcrete(candidate)) {
                registerCommand(candidate);
            }
            if (IModEvent.class.isAssignableFrom(candidate) && isConcrete(candidate)) {
                registerEvent(candidate);
            }
        }
    }

    private boolean isConcrete(Class<?> candidate) {
        return !candidate.isInterface() && !Modifier.isAbstract(candidate.getModifiers());
    }

    private void registerCommand(Class<?> candidate) {
        try {
            IModCommand command = (IModCommand) candidate.getDeclaredConstructor().newInstance();
            LOGGER.atInfo().log("Registering command: %s",  candidate.getName());
            command.register(this);
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("Failed to register command %s: %s", candidate.getName(), e.getMessage());
        }
    }

    private void registerEvent(Class<?> candidate) {
        try {
            IModEvent event = (IModEvent) candidate.getDeclaredConstructor().newInstance();
            LOGGER.atInfo().log("Registering event: %s",  candidate.getName());
            event.register(this);
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("Failed to register event %s: %s", candidate.getName(), e.getMessage());
        }
    }

    public IServiceProvider getServiceProvider() {
        return serviceProvider;
    }
}
