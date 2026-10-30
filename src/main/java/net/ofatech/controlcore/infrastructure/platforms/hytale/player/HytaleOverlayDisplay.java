package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.ports.game.OverlayDisplay;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleOverlayDisplay implements OverlayDisplay {

    static final String KEY = "ControlCore_Overlay";
    private static final String LAYOUT = "Pages/ControlCore/Overlay.ui";
    private static final String TOAST = "Pages/ControlCore/OverlayToast.ui";
    private static final String WARNING = "Pages/ControlCore/OverlayWarning.ui";
    private static final Map<String, String> CORNERS = Map.of(
            Overlay.TOP_RIGHT, "#ToastsTopRight",
            Overlay.TOP_LEFT, "#ToastsTopLeft",
            Overlay.BOTTOM_RIGHT, "#ToastsBottomRight");
    private static final Map<String, String> PLACES =
            Map.of(Overlay.UNDER_COMPASS, "#WarningsUnderCompass", Overlay.BOTTOM, "#WarningsBottom");
    private static final List<String> LEVELS = List.of("Info", "Success", "Warning", "Error");

    private final Log log;

    public HytaleOverlayDisplay(Log log) {
        this.log = log;
    }

    @Override
    public void render(PlayerId player, Overlay overlay) {
        onPlayer(player, (playerRef, hud) -> {
            CustomUIHud existing = hud.getCustomHud(KEY);
            if (existing instanceof OverlayHud overlayHud) {
                UICommandBuilder commands = new UICommandBuilder();
                fill(commands, overlay);
                overlayHud.content = overlay;
                existing.update(false, commands);
            } else if (!overlay.empty()) {
                hud.addCustomHud(playerRef, new OverlayHud(playerRef, overlay));
            }
        });
    }

    @Override
    public void hytaleToast(PlayerId player, String title, String text, ToastLevel level) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        if (playerRef == null) {
            return;
        }
        NotificationStyle style = switch (level) {
            case INFO -> NotificationStyle.Default;
            case SUCCESS -> NotificationStyle.Success;
            case WARNING -> NotificationStyle.Warning;
            case ERROR -> NotificationStyle.Danger;
        };
        try {
            if (title.isBlank()) {
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw(text), style);
            } else {
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(), Message.raw(title), Message.raw(text), style);
            }
        } catch (RuntimeException e) {
            log.error("Could not send a notification to " + playerRef.getUsername(), e);
        }
    }

    private void onPlayer(PlayerId player, BiConsumer<PlayerRef, HudManager> action) {
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
                if (component != null) {
                    action.accept(playerRef, component.getHudManager());
                }
            } catch (RuntimeException e) {
                log.error("Could not update the overlay of " + playerRef.getUsername(), e);
            }
        });
    }

    static void fill(UICommandBuilder commands, Overlay overlay) {
        CORNERS.values().forEach(commands::clear);
        PLACES.values().forEach(commands::clear);
        String corner = CORNERS.getOrDefault(overlay.toastCorner(), CORNERS.get(Overlay.TOP_RIGHT));
        int toastsHeight = 0;
        for (int index = 0; index < overlay.toasts().size(); index++) {
            Overlay.Line toast = overlay.toasts().get(index);
            String row = corner + "[" + index + "]";
            commands.append(corner, TOAST);
            levels(commands, row, toast.level());
            commands.set(row + " #Title.Visible", !toast.title().isBlank());
            commands.set(row + " #Title.Text", toast.title());
            commands.set(row + " #Text.Text", toast.text());
            commands.setObject(row + ".Anchor", toastAnchor(toast));
            toastsHeight += OverlaySize.toastHeight(toast.title(), toast.text()) + 6;
        }
        if (corner.equals(CORNERS.get(Overlay.BOTTOM_RIGHT))) {
            commands.setObject(corner + ".Anchor", bottomBox(170, 24, 380, toastsHeight));
        }
        int warningsHeight = 0;
        String place = PLACES.getOrDefault(overlay.warningPlace(), PLACES.get(Overlay.UNDER_COMPASS));
        for (int index = 0; index < overlay.warnings().size(); index++) {
            Overlay.Line warning = overlay.warnings().get(index);
            String row = place + "[" + index + "]";
            commands.append(place, WARNING);
            levels(commands, row, warning.level());
            commands.set(row + " #Text.Text", warning.text());
            commands.setObject(row + ".Anchor", warningAnchor(warning.text()));
            warningsHeight += OverlaySize.warningHeight(warning.text()) + 4;
        }
        if (place.equals(PLACES.get(Overlay.BOTTOM))) {
            commands.setObject(place + ".Anchor", bottomBox(150, null, 640, warningsHeight));
        }
    }

    private static Anchor bottomBox(int bottom, Integer right, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setBottom(Value.of(bottom));
        if (right != null) {
            anchor.setRight(Value.of(right));
        }
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(Math.max(1, height)));
        return anchor;
    }

    private static Anchor toastAnchor(Overlay.Line toast) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(OverlaySize.toastHeight(toast.title(), toast.text())));
        anchor.setBottom(Value.of(6));
        return anchor;
    }

    private static Anchor warningAnchor(String text) {
        Anchor anchor = new Anchor();
        anchor.setHeight(Value.of(OverlaySize.warningHeight(text)));
        anchor.setBottom(Value.of(4));
        return anchor;
    }

    private static void levels(UICommandBuilder commands, String row, ToastLevel level) {
        for (int index = 0; index < LEVELS.size(); index++) {
            commands.set(row + " #Bar" + LEVELS.get(index) + ".Visible", level.ordinal() == index);
        }
    }

    private static final class OverlayHud extends CustomUIHud {

        private volatile Overlay content;

        OverlayHud(PlayerRef playerRef, Overlay content) {
            super(playerRef, KEY, 50);
            this.content = content;
        }

        @Override
        protected void build(UICommandBuilder commands) {
            commands.append(LAYOUT);
            fill(commands, content);
        }
    }
}
