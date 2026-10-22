package net.ofatech.controlcore.infrastructure.platforms.custominventory;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.controlpanel.ControlPanelService;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.system.Log;

final class CustomInventoryButton {

    static final String BUTTON_ID = "controlcore:panel";
    static final String BUTTON_TITLE = "ControlCore";
    static final int BUTTON_ORDER = 100;

    private static final String API = "com.supremosan.custominventory.";

    private CustomInventoryButton() {}

    static AutoCloseable install(ClassLoader modClasses, ControlPanelService panel, Log log) {
        Consumer<Object> openPanel = context -> {
            PlayerId player = PlayerId.of(playerUuid(context));
            panel.openBestFor(player)
                    .onFailure(error -> log.warn(
                            "Could not open the control panel for " + player + " from the inventory: " + error.code()));
        };
        return register(modClasses, openPanel);
    }

    private static AutoCloseable register(ClassLoader modClasses, Consumer<Object> action) {
        try {
            Class<?> pluginType = Class.forName(API + "CustomInventoryPlugin", true, modClasses);
            Class<?> definitionType = Class.forName(API + "api.InventoryButtonDefinition", true, modClasses);
            Object plugin = pluginType.getMethod("get").invoke(null);
            Object registry = pluginType.getMethod("getInventoryRegistry").invoke(plugin);
            Object definition = definitionType
                    .getConstructor(String.class, String.class, int.class, Consumer.class)
                    .newInstance(BUTTON_ID, BUTTON_TITLE, BUTTON_ORDER, action);
            return (AutoCloseable) registry.getClass()
                    .getMethod("registerInventoryButton", definitionType)
                    .invoke(registry, definition);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("CustomInventory rejected the ControlCore button", e.getCause());
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("This CustomInventory version has no supported button API", e);
        }
    }

    private static UUID playerUuid(Object context) {
        try {
            Object playerRef = context.getClass().getMethod("playerRef").invoke(context);
            return (UUID) playerRef.getClass().getMethod("getUuid").invoke(playerRef);
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            throw new IllegalStateException("CustomInventory passed an unexpected context", e);
        }
    }
}
