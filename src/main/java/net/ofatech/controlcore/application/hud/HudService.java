package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.api.HudApi;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.HudElement;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.game.HudDisplay;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(HudApi.class)
public final class HudService implements HudApi {

    static final String NAMESPACE = "controlcore";
    static final String PART_PREFIX = "hud.";
    static final String RULE_PREFIX = "hud-rule.";

    static final String PLAYERS_CHOOSE = "Players choose";
    static final String HIDDEN_UNTIL_CHOSEN = "Hidden until a player turns it on";
    static final String ALWAYS_HIDDEN = "Always hidden";
    static final String ALWAYS_SHOWN = "Always shown";

    private static final Text CATEGORY = Text.key("controlcore.hud.category", "HUD");

    private final SettingsApi settings;
    private final HudDisplay display;
    private final Players players;
    private final Log log;
    private final Map<HudPart, Setting<Boolean>> preferences = new EnumMap<>(HudPart.class);
    private final Map<HudPart, Setting<String>> rules = new EnumMap<>(HudPart.class);
    private final Map<SettingId, HudElement> elements = new ConcurrentHashMap<>();
    private final Set<PlayerId> hiddenAll = ConcurrentHashMap.newKeySet();
    private final Map<PlayerId, Set<HudPart>> hiddenByUs = new ConcurrentHashMap<>();
    private final Map<PlayerId, Map<SettingId, Boolean>> shownElements = new ConcurrentHashMap<>();

    public HudService(SettingsApi settings, HudDisplay display, Players players, Log log) {
        this.settings = settings;
        this.display = display;
        this.players = players;
        this.log = log;
        for (HudPart part : HudPart.values()) {
            preferences.put(
                    part,
                    settings.register(SettingDefinition.player(
                                            NAMESPACE, PART_PREFIX + part.slug(), SettingType.bool(), true)
                                    .named(part.nameText())
                                    .describedAs(Text.key(
                                            "controlcore.settings.hud_part.desc",
                                            "Show this part of the HUD. The server can force it on or off."))
                                    .inCategory(CATEGORY))
                            .value());
            rules.put(
                    part,
                    settings.register(SettingDefinition.server(
                                            NAMESPACE,
                                            RULE_PREFIX + part.slug(),
                                            SettingType.choice(
                                                    PLAYERS_CHOOSE, HIDDEN_UNTIL_CHOSEN, ALWAYS_HIDDEN, ALWAYS_SHOWN),
                                            PLAYERS_CHOOSE)
                                    .named(part.nameText())
                                    .describedAs(Text.key(
                                            "controlcore.settings.hud_rule.desc",
                                            "Who decides whether players see this part of the HUD."))
                                    .inCategory(CATEGORY)
                                    .optionsKey("controlcore.hud.rules"))
                            .value());
        }
    }

    @Override
    public Result<HudElement> register(HudElement element) {
        Result<Setting<Boolean>> preference = settings.register(SettingDefinition.player(
                        element.preferenceId().namespace(),
                        element.preferenceId().key(),
                        SettingType.bool(),
                        element.visibleByDefault())
                .named(element.nameText())
                .describedAs(element.descriptionText())
                .inCategory(CATEGORY));
        if (preference.isFailure()) {
            return Result.failure(preference.error());
        }
        elements.put(element.id(), element);
        log.debug("HUD " + element.id() + " registered");
        players.online().forEach(player -> applyElements(player.id()));
        return Result.success(element);
    }

    @Override
    public List<HudElement> elements() {
        List<HudElement> list = new ArrayList<>(elements.values());
        list.sort((a, b) -> a.id().compareTo(b.id()));
        return list;
    }

    @Override
    public boolean isVisible(PlayerId player, SettingId id) {
        HudElement element = elements.get(id);
        if (element == null || hiddenAll.contains(player)) {
            return false;
        }
        return settings.read(element.preferenceId(), Optional.of(player))
                .map(snapshot -> Boolean.parseBoolean(snapshot.value()))
                .orElse(element.visibleByDefault());
    }

    @Override
    public boolean isVisible(PlayerId player, HudPart part) {
        if (hiddenAll.contains(player)) {
            return false;
        }
        String rule = rules.get(part).get();
        if (ALWAYS_HIDDEN.equals(rule)) {
            return false;
        }
        if (ALWAYS_SHOWN.equals(rule)) {
            return true;
        }
        Result<SettingSnapshot> own =
                settings.read(preferences.get(part).definition().id(), Optional.of(player));
        if (own.isSuccess() && own.value().overridden()) {
            return Boolean.parseBoolean(own.value().value());
        }
        return !HIDDEN_UNTIL_CHOSEN.equals(rule);
    }

    @Override
    public boolean isHidden(PlayerId player) {
        return hiddenAll.contains(player);
    }

    @Override
    public void setHidden(PlayerId player, boolean hidden) {
        boolean changed = hidden ? hiddenAll.add(player) : hiddenAll.remove(player);
        if (changed) {
            apply(player);
        }
    }

    public void apply(PlayerId player) {
        applyParts(player);
        applyElements(player);
    }

    void onSettingChanged(SettingChanged change) {
        SettingId id = change.id();
        if (NAMESPACE.equals(id.namespace()) && id.key().startsWith(RULE_PREFIX)) {
            players.online().forEach(player -> applyParts(player.id()));
        } else if (NAMESPACE.equals(id.namespace()) && id.key().startsWith(PART_PREFIX)) {
            change.player().ifPresent(this::applyParts);
        } else if (elements.values().stream()
                .anyMatch(element -> element.preferenceId().equals(id))) {
            change.player().ifPresent(this::applyElements);
        }
    }

    void forget(PlayerId player) {
        hiddenAll.remove(player);
        hiddenByUs.remove(player);
        shownElements.remove(player);
    }

    private void applyParts(PlayerId player) {
        Set<HudPart> hide = EnumSet.noneOf(HudPart.class);
        for (HudPart part : HudPart.values()) {
            if (!isVisible(player, part)) {
                hide.add(part);
            }
        }
        Set<HudPart> before = hiddenByUs.getOrDefault(player, EnumSet.noneOf(HudPart.class));
        Set<HudPart> show = EnumSet.noneOf(HudPart.class);
        show.addAll(before);
        show.removeAll(hide);
        hiddenByUs.put(player, hide);
        if (!hide.isEmpty() || !show.isEmpty()) {
            display.update(player, hide, show);
        }
    }

    private void applyElements(PlayerId player) {
        Map<SettingId, Boolean> shown = shownElements.computeIfAbsent(player, key -> new HashMap<>());
        for (HudElement element : elements()) {
            boolean visible = isVisible(player, element.id());
            Boolean before = shown.put(element.id(), visible);
            if (before != null && before == visible) {
                continue;
            }
            if (before == null && !visible) {
                continue;
            }
            try {
                (visible ? element.onShow() : element.onHide()).accept(player);
            } catch (RuntimeException e) {
                log.error("The HUD " + element.id() + " failed to " + (visible ? "show" : "hide"), e);
            }
        }
    }

    List<PlayerId> online() {
        return players.online().stream().map(PlayerInfo::id).toList();
    }
}
