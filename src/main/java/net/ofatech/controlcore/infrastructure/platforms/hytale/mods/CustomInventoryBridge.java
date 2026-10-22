package net.ofatech.controlcore.infrastructure.platforms.hytale.mods;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import net.ofatech.controlcore.application.controlpanel.ControlPanelService;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.system.Log;

final class CustomInventoryBridge {

    static final String BUTTON_ID = "controlcore:panel";
    static final String BUTTON_TITLE = "ControlCore";
    static final int BUTTON_ORDER = 100;

    private static final String API = "com.supremosan.custominventory.";

    private CustomInventoryBridge() {}

    static AutoCloseable install(ControlPanelService panel, Log log) {
        Consumer<Object> openPanel = context -> {
            PlayerRef player = playerOf(context);
            panel.openBestFor(PlayerId.of(player.getUuid()))
                    .onFailure(error -> log.warn("Could not open the control panel for " + player.getUsername()
                            + " from the inventory: " + error.code()));
        };
        return register(openPanel);
    }

    private static AutoCloseable register(Consumer<Object> action) {
        try {
            ClassLoader loader = pluginLoader();
            Class<?> pluginType = Class.forName(API + "CustomInventoryPlugin", true, loader);
            Class<?> definitionType = Class.forName(API + "api.InventoryButtonDefinition", true, loader);
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

    private static ClassLoader pluginLoader() {
        PluginBase plugin = PluginManager.get().getPlugin(PluginIdentifier.fromString(CustomInventoryButton.MOD_ID));
        if (plugin == null) {
            throw new IllegalStateException(CustomInventoryButton.MOD_ID + " is not loaded");
        }
        return plugin.getClass().getClassLoader();
    }

    private static PlayerRef playerOf(Object context) {
        try {
            return (PlayerRef) context.getClass().getMethod("playerRef").invoke(context);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("CustomInventory passed an unexpected context", e);
        }
    }
}
