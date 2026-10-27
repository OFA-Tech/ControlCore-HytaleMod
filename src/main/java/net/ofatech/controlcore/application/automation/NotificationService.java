package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.ofatech.controlcore.api.HudApi;
import net.ofatech.controlcore.api.NotificationsApi;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.HudElement;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.Messenger;
import net.ofatech.controlcore.domain.ports.game.OverlayDisplay;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

@AutoService(NotificationsApi.class)
public final class NotificationService implements NotificationsApi {

    static final int MAX_TOASTS = 3;
    static final int MAX_TEXT = 200;
    static final String NAMESPACE = "controlcore";

    static final String TOP_RIGHT = "Top right";
    static final String TOP_LEFT = "Top left";
    static final String BOTTOM_RIGHT = "Bottom right";
    static final String HYTALE = "Hytale notifications";
    static final String UNDER_COMPASS = "Under the compass";
    static final String ABOVE_HOTBAR = "Above the hotbar";

    private static final Map<String, String> CORNERS = Map.of(
            TOP_RIGHT, Overlay.TOP_RIGHT,
            TOP_LEFT, Overlay.TOP_LEFT,
            BOTTOM_RIGHT, Overlay.BOTTOM_RIGHT,
            HYTALE, Overlay.HYTALE);
    private static final Map<String, String> PLACES =
            Map.of(UNDER_COMPASS, Overlay.UNDER_COMPASS, ABOVE_HOTBAR, Overlay.BOTTOM);
    private static final Text CATEGORY = Text.key("controlcore.hud.category", "HUD");

    private final Messenger messenger;
    private final Players players;
    private final SettingPermissionPolicy policy;
    private final OverlayDisplay display;
    private final Scheduler scheduler;
    private final HudApi huds;
    private final Setting<String> toastPosition;
    private final Setting<String> warningPosition;
    private final HudElement element;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<PlayerId, List<Entry>> toasts = new ConcurrentHashMap<>();
    private final Map<PlayerId, Map<String, Entry>> warnings = new ConcurrentHashMap<>();
    private final Map<String, Entry> everyone = new ConcurrentHashMap<>();
    private final Set<PlayerId> shown = ConcurrentHashMap.newKeySet();

    private record Entry(long serial, Overlay.Line line) {}

    public NotificationService(
            Messenger messenger,
            Players players,
            SettingPermissionPolicy policy,
            OverlayDisplay display,
            Scheduler scheduler,
            SettingsApi settings,
            HudApi huds) {
        this.messenger = messenger;
        this.players = players;
        this.policy = policy;
        this.display = display;
        this.scheduler = scheduler;
        this.huds = huds;
        this.toastPosition = settings.register(SettingDefinition.player(
                                NAMESPACE,
                                "toast-position",
                                SettingType.choice(TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, HYTALE),
                                TOP_RIGHT)
                        .named(Text.key("controlcore.settings.toast_position.name", "Toast position"))
                        .describedAs(Text.key(
                                "controlcore.settings.toast_position.desc",
                                "Where small timed messages appear, or Hytale's own notifications."))
                        .inCategory(CATEGORY)
                        .optionsKey("controlcore.notifications.corners"))
                .value();
        this.warningPosition = settings.register(SettingDefinition.player(
                                NAMESPACE,
                                "warning-position",
                                SettingType.choice(UNDER_COMPASS, ABOVE_HOTBAR),
                                UNDER_COMPASS)
                        .named(Text.key("controlcore.settings.warning_position.name", "Warning position"))
                        .describedAs(Text.key(
                                "controlcore.settings.warning_position.desc",
                                "Where warning bars such as \"Restart in 10 minutes\" appear."))
                        .inCategory(CATEGORY)
                        .optionsKey("controlcore.notifications.places"))
                .value();
        this.element = HudElement.of(NAMESPACE, "toasts")
                .displayName(Text.key("controlcore.hud.notifications.name", "Toasts and warnings"))
                .describedAs(Text.key(
                        "controlcore.hud.notifications.desc",
                        "Small timed messages and warning bars from the server and its mods."))
                .onShow(player -> {
                    shown.add(player);
                    render(player);
                })
                .onHide(player -> {
                    shown.remove(player);
                    display.render(player, new Overlay(List.of(), Overlay.TOP_RIGHT, List.of(), Overlay.UNDER_COMPASS));
                });
        huds.register(element);
    }

    @Override
    public void message(PlayerId player, String text) {
        messenger.send(player, text);
    }

    @Override
    public void message(PlayerId player, String text, String color) {
        messenger.send(player, text, color);
    }

    @Override
    public void broadcast(String text) {
        messenger.broadcast(text);
    }

    @Override
    public void toAdmins(String text) {
        for (PlayerInfo player : players.online()) {
            if (policy.isAdmin(player.id())) {
                messenger.send(player.id(), text);
            }
        }
    }

    @Override
    public void title(PlayerId player, String title, String subtitle) {
        messenger.title(player, title, subtitle == null ? "" : subtitle);
    }

    @Override
    public void titleAll(String title, String subtitle) {
        messenger.titleAll(title, subtitle == null ? "" : subtitle);
    }

    @Override
    public void toast(PlayerId player, String title, String text, ToastLevel level, int seconds) {
        if (player == null || !visible(player)) {
            return;
        }
        Overlay.Line line = line(title, text, level);
        if (HYTALE.equals(toastPosition.get(player))) {
            display.hytaleToast(player, line.title(), line.text(), line.level());
            return;
        }
        Entry entry = new Entry(sequence.incrementAndGet(), line);
        List<Entry> list = toasts.computeIfAbsent(player, key -> new ArrayList<>());
        synchronized (list) {
            list.add(entry);
            while (list.size() > MAX_TOASTS) {
                list.removeFirst();
            }
        }
        render(player);
        scheduler.runLater(Duration.ofSeconds(Math.clamp(seconds, 1, 60)), () -> {
            boolean removed;
            synchronized (list) {
                removed = list.remove(entry);
            }
            if (removed) {
                render(player);
            }
        });
    }

    @Override
    public void toastAll(String title, String text, ToastLevel level, int seconds) {
        players.online().forEach(player -> toast(player.id(), title, text, level, seconds));
    }

    @Override
    public void warning(PlayerId player, String id, String text, ToastLevel level, int seconds) {
        if (player == null || id == null || id.isBlank()) {
            return;
        }
        Entry entry = new Entry(sequence.incrementAndGet(), line("", text, level));
        warnings.computeIfAbsent(player, key -> new ConcurrentHashMap<>()).put(id, entry);
        render(player);
        expire(seconds, () -> {
            Map<String, Entry> own = warnings.get(player);
            if (own != null && own.remove(id, entry)) {
                render(player);
            }
        });
    }

    @Override
    public void warningAll(String id, String text, ToastLevel level, int seconds) {
        if (id == null || id.isBlank()) {
            return;
        }
        Entry entry = new Entry(sequence.incrementAndGet(), line("", text, level));
        everyone.put(id, entry);
        renderAll();
        expire(seconds, () -> {
            if (everyone.remove(id, entry)) {
                renderAll();
            }
        });
    }

    @Override
    public void clearWarning(PlayerId player, String id) {
        Map<String, Entry> own = warnings.get(player);
        if (own != null && own.remove(id) != null) {
            render(player);
        }
    }

    @Override
    public void clearWarningAll(String id) {
        boolean changed = everyone.remove(id) != null;
        for (Map<String, Entry> own : warnings.values()) {
            changed |= own.remove(id) != null;
        }
        if (changed) {
            renderAll();
        }
    }

    public List<String> warningIds() {
        List<String> ids = new ArrayList<>(everyone.keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    public void onSettingChanged(SettingChanged change) {
        if (NAMESPACE.equals(change.id().namespace())
                && (change.id().key().equals("toast-position")
                        || change.id().key().equals("warning-position"))) {
            change.player().ifPresent(this::render);
        }
    }

    void forget(PlayerId player) {
        toasts.remove(player);
        warnings.remove(player);
        shown.remove(player);
    }

    void join(PlayerId player) {
        if (!everyone.isEmpty()) {
            render(player);
        }
    }

    Optional<Overlay> overlayOf(PlayerId player) {
        if (!visible(player)) {
            return Optional.empty();
        }
        List<Overlay.Line> toastLines = new ArrayList<>();
        List<Entry> list = toasts.get(player);
        if (list != null) {
            synchronized (list) {
                list.forEach(entry -> toastLines.add(entry.line()));
            }
        }
        Map<String, Entry> merged = new LinkedHashMap<>(everyone);
        Map<String, Entry> own = warnings.get(player);
        if (own != null) {
            merged.putAll(own);
        }
        List<Overlay.Line> warningLines = merged.values().stream()
                .sorted((a, b) -> Long.compare(a.serial(), b.serial()))
                .map(Entry::line)
                .toList();
        return Optional.of(new Overlay(
                toastLines,
                CORNERS.getOrDefault(toastPosition.get(player), Overlay.TOP_RIGHT),
                warningLines,
                PLACES.getOrDefault(warningPosition.get(player), Overlay.UNDER_COMPASS)));
    }

    private boolean visible(PlayerId player) {
        return huds.isVisible(player, element.id());
    }

    private void render(PlayerId player) {
        overlayOf(player).ifPresent(overlay -> display.render(player, overlay));
    }

    private void renderAll() {
        players.online().forEach(player -> render(player.id()));
    }

    private void expire(int seconds, Runnable work) {
        if (seconds > 0) {
            scheduler.runLater(Duration.ofSeconds(seconds), work);
        }
    }

    private static Overlay.Line line(String title, String text, ToastLevel level) {
        return new Overlay.Line(
                clip(title == null ? "" : title.strip()),
                clip(text == null ? "" : text.strip()),
                level == null ? ToastLevel.INFO : level);
    }

    private static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
