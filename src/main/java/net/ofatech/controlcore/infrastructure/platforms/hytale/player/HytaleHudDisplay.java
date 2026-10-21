package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.ports.game.HudDisplay;
import net.ofatech.controlcore.domain.ports.system.Log;

/** {@link HudDisplay} through the player's {@code HudManager}, on the player's world thread. */
public final class HytaleHudDisplay implements HudDisplay {

    private static final Map<HudPart, HudComponent> COMPONENTS = new EnumMap<>(HudPart.class);

    static {
        for (HudPart part : HudPart.values()) {
            COMPONENTS.put(part, HudComponent.valueOf(pascalCase(part.name())));
        }
    }

    private final Log log;

    public HytaleHudDisplay(Log log) {
        this.log = log;
    }

    @Override
    public void update(PlayerId player, Set<HudPart> hide, Set<HudPart> show) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            return;
        }
        Store<EntityStore> store = entity.getStore();
        store.getExternalData().getWorld().execute(() -> {
            try {
                if (!entity.isValid()) {
                    return;
                }
                Player component = store.getComponent(entity, Player.getComponentType());
                if (component == null) {
                    return;
                }
                if (!show.isEmpty()) {
                    component.getHudManager().showHudComponents(playerRef, components(show));
                }
                if (!hide.isEmpty()) {
                    component.getHudManager().hideHudComponents(playerRef, components(hide));
                }
            } catch (RuntimeException e) {
                log.error("Could not update the HUD of " + playerRef.getUsername(), e);
            }
        });
    }

    private static HudComponent[] components(Set<HudPart> parts) {
        return parts.stream().map(COMPONENTS::get).toArray(HudComponent[]::new);
    }

    private static String pascalCase(String constant) {
        StringBuilder name = new StringBuilder();
        for (String word : constant.split("_")) {
            name.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }
}
