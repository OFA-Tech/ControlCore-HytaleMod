package net.ofatech.hytaletemplate;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import net.ofatech.hytaletemplate.core.domain.interfaces.IModCommand;
import net.ofatech.hytaletemplate.core.domain.interfaces.IModEvent;
import net.ofatech.hytaletemplate.platform.registry.ModComponentScanner;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.util.Set;

public class TemplatePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TemplatePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Initializing TemplatePlugin  with Version: %s", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        registerDynamicComponents();
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
            command.register(this);
        } catch (ReflectiveOperationException e) {
            // Ignore failed registrations to avoid blocking startup.
        }
    }

    private void registerEvent(Class<?> candidate) {
        try {
            IModEvent event = (IModEvent) candidate.getDeclaredConstructor().newInstance();
            event.register(this);
        } catch (ReflectiveOperationException e) {
            // Ignore failed registrations to avoid blocking startup.
        }
    }
}
