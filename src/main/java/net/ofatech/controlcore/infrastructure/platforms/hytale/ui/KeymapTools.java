package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.result.Result;

final class KeymapTools {

    private static final String KEYMAP_MODE_SERVER = "KeymapMode:Server";
    private static final String KEYMAP_MODE_MINE = "KeymapMode:Mine";
    private static final String CUSTOM_NAMESPACE = "custom";
    private static final String PROBE_TOGGLE = "ProbeToggle";
    private static final String KEY_TEST = "KeyTest";
    private static final String LAYOUT = ControlPanelPage.LAYOUT;

    interface Host {
        void send(UICommandBuilder update);

        void showLegend(UICommandBuilder update);

        SettingSnapshot latestOf(PanelSection key, String idText);

        Integer indexOf(PanelSection key, String idText);

        Result<SettingSnapshot> saveValue(
                PanelSection key,
                SettingsSection section,
                String id,
                Supplier<Result<SettingSnapshot>> change,
                boolean controlAlreadyShowsValue,
                UICommandBuilder update);
    }

    private final Map<PanelSection, SectionState> states;
    private final CustomKeyBindEditor customKeyBinds;
    private final Optional<KeyBindForm> bindForm;
    private final Optional<InputProbe> probe;
    private final PanelKeyDetect keyDetect;
    private final PlayerId viewerId;
    private final Translator tr;
    private final Host host;
    private boolean editingServerKeys = true;

    KeymapTools(
            Map<PanelSection, SectionState> states,
            CustomKeyBindEditor customKeyBinds,
            Optional<KeyBindForm> bindForm,
            Optional<InputProbe> probe,
            PanelKeyDetect keyDetect,
            PlayerId viewerId,
            Translator tr,
            Host host) {
        this.states = states;
        this.customKeyBinds = customKeyBinds;
        this.bindForm = bindForm;
        this.probe = probe;
        this.keyDetect = keyDetect;
        this.viewerId = viewerId;
        this.tr = tr;
        this.host = host;
    }

    PanelSection keymapSection() {
        return editingServerKeys && bindForm.isPresent() ? PanelSection.SERVER_KEYMAPS : PanelSection.KEYMAPS;
    }

    boolean customBind(PanelSection key, SettingId id) {
        return bindForm.isPresent() && key == PanelSection.SERVER_KEYMAPS && CUSTOM_NAMESPACE.equals(id.namespace());
    }

    Optional<GameActionMode> gameAction(PanelSection key, SettingId id) {
        return bindForm.isPresent() && key == PanelSection.SERVER_KEYMAPS
                ? Optional.of(customKeyBinds.gameAction(id))
                : Optional.empty();
    }

    void buildDebugTools(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#DebugTools.Visible", probe.isPresent());
        if (probe.isEmpty()) {
            return;
        }
        showProbeButton(commands);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#ProbeToggle", EventData.of(PanelAction.KEY, PROBE_TOGGLE));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#KeyTest", EventData.of(PanelAction.KEY, KEY_TEST));
    }

    void buildModeSwitch(UICommandBuilder commands, UIEventBuilder events) {
        bindForm.ifPresent(form -> {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#KeymapModeServer",
                    EventData.of(PanelAction.KEY, KEYMAP_MODE_SERVER));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#KeymapModeMine",
                    EventData.of(PanelAction.KEY, KEYMAP_MODE_MINE));
            form.build(commands, events);
        });
    }

    boolean handle(PanelAction action) {
        String name = action.name();
        if (PROBE_TOGGLE.equals(name) || KEY_TEST.equals(name)) {
            handleDebug(name);
        } else if (KEYMAP_MODE_SERVER.equals(name) || KEYMAP_MODE_MINE.equals(name)) {
            if (bindForm.isPresent()) {
                editingServerKeys = KEYMAP_MODE_SERVER.equals(name);
                UICommandBuilder update = new UICommandBuilder();
                showKeymapMode(update);
                host.showLegend(update);
                host.send(update);
            }
        } else {
            return false;
        }
        return true;
    }

    boolean handleRow(PanelSection key, SettingsSection section, String name, PanelAction action) {
        if (SettingRows.PICK_GAME_ACTION.equals(name)) {
            pickGameAction(key, action);
        } else if (SettingRows.DETECT.equals(name)) {
            detectOnRow(key, section, action);
        } else {
            return false;
        }
        return true;
    }

    private void showProbeButton(UICommandBuilder commands) {
        probe.ifPresent(tool -> commands.set(
                "#ProbeToggle.Text",
                tr.t(
                        tool.isOn(viewerId)
                                ? "controlcore.panel.debug.probe_stop"
                                : "controlcore.panel.debug.probe_start")));
    }

    private void handleDebug(String name) {
        if (probe.isEmpty()) {
            return;
        }
        InputProbe tool = probe.get();
        UICommandBuilder update = new UICommandBuilder();
        if (PROBE_TOGGLE.equals(name)) {
            boolean on = tool.toggle(viewerId);
            showProbeButton(update);
            SettingRowEditor.footer(update, tr.t(on ? "controlcore.debug.probe_on" : "controlcore.debug.probe_off"));
        } else {
            Result<Void> opened = tool.openKeyTest(viewerId, InputProbe.KEY_TEST_TARGETS);
            if (opened.isSuccess()) {
                return;
            }
            SettingRowEditor.footer(update, tr.error(opened.error()));
        }
        host.send(update);
    }

    private void detectOnRow(PanelSection key, SettingsSection section, PanelAction action) {
        SettingSnapshot current = host.latestOf(key, action.id);
        if (current == null || section.kind(current) != SettingsSection.Kind.KEYS) {
            UICommandBuilder update = new UICommandBuilder();
            SettingRowEditor.footer(update, tr.t("controlcore.keys.detect.gone"));
            host.send(update);
            return;
        }
        if (current.access() == SettingAccess.READ_ONLY) {
            UICommandBuilder update = new UICommandBuilder();
            SettingRowEditor.footer(update, tr.t("controlcore.keys.detect.locked"));
            host.send(update);
            return;
        }
        String id = action.id;
        keyDetect.start(
                key == PanelSection.SERVER_KEYMAPS ? PanelKeyDetect.Target.SERVER_KEY : PanelKeyDetect.Target.OWN_KEY,
                (keys, mode) -> {
                    SettingSnapshot latest = host.latestOf(key, id);
                    if (latest == null) {
                        return tr.t("controlcore.keys.detect.gone");
                    }
                    if (latest.access() == SettingAccess.READ_ONLY) {
                        return tr.t("controlcore.keys.detect.locked");
                    }
                    UICommandBuilder update = new UICommandBuilder();
                    String value = KeyPicker.withDetected(keys, latest.value());
                    Result<SettingSnapshot> saved =
                            host.saveValue(key, section, id, () -> section.save(id, value), false, update);
                    String text = saved.isSuccess()
                            ? detectedText(key, section, saved.value(), keys, mode)
                            : tr.error(saved.error());
                    SettingRowEditor.footer(update, text);
                    host.send(update);
                    return null;
                });
    }

    private String detectedText(
            PanelSection key, SettingsSection section, SettingSnapshot saved, String keys, KeyDetection.Mode mode) {
        String described = KeyPicker.describe(tr, keys);
        StringBuilder text = new StringBuilder(
                mode == KeyDetection.Mode.GAME || KeyPicker.followsGameAction(keys)
                        ? tr.t("controlcore.keys.detect.saved_game", "keys", described)
                        : tr.t("controlcore.keys.detect.saved", "keys", described));
        if (!KeyPicker.heard(keys)) {
            text.append(' ').append(tr.t("controlcore.keys.detect.unheard"));
        }
        text.append(conflictNote(key, section, saved));
        return text.toString();
    }

    String conflictNote(PanelSection key, SettingsSection section, SettingSnapshot saved) {
        SectionState state = states.get(key);
        List<String> others = state.indexes().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .filter(id -> !id.equals(saved.id()))
                .map(state::latest)
                .filter(other -> other != null && KeyPicker.sharesKeys(other.value(), saved.value()))
                .map(section::name)
                .toList();
        return others.isEmpty()
                ? ""
                : " " + tr.t("controlcore.keys.detect.also_used", "actions", String.join(", ", others));
    }

    private void pickGameAction(PanelSection key, PanelAction action) {
        UICommandBuilder update = new UICommandBuilder();
        if (key != PanelSection.SERVER_KEYMAPS || bindForm.isEmpty()) {
            host.send(update);
            return;
        }
        Result<SettingId> id = SettingId.parse(action.id == null ? "" : action.id);
        Integer index = host.indexOf(key, action.id);
        if (id.isSuccess()) {
            Optional<GameActionMode> wanted = GameActionMode.parse(action.value);
            Result<GameActionMode> saved =
                    wanted.isPresent() ? customKeyBinds.setGameAction(id.value(), wanted.get()) : null;
            if (saved != null && saved.isSuccess()) {
                SettingSnapshot row = host.latestOf(key, action.id);
                SettingRowEditor.footer(
                        update,
                        tr.t(
                                "controlcore.keys.game_action.saved",
                                "name",
                                row == null
                                        ? action.id
                                        : tr.translate(row.definition().nameText()),
                                "mode",
                                SettingRows.gameActionLabel(tr, saved.value())));
            } else if (saved != null) {
                SettingRowEditor.footer(update, tr.error(saved.error()));
            }
            if (index != null) {
                update.set(
                        key.row(index) + " #GameAction.Value",
                        customKeyBinds.gameAction(id.value()).id());
            }
        }
        host.send(update);
    }

    void showKeymapMode(UICommandBuilder commands) {
        boolean admin = bindForm.isPresent();
        boolean server = admin && editingServerKeys;
        commands.set("#KeymapModeButtons.Visible", admin);
        commands.set("#ServerKeymaps.Visible", server);
        commands.set("#MyKeymaps.Visible", !server);
        commands.set("#NewBindToggle.Visible", server);
        commands.set("#KeymapModeServer.Style", Value.ref(LAYOUT, server ? "SegmentOnStyle" : "SegmentStyle"));
        commands.set("#KeymapModeMine.Style", Value.ref(LAYOUT, server ? "SegmentStyle" : "SegmentOnStyle"));
        listHeights(commands);
    }

    void listHeights(UICommandBuilder commands) {
        boolean admin = bindForm.isPresent();
        int server = 536
                - (probe.isPresent() ? 42 : 0)
                - (bindForm.map(KeyBindForm::isOpen).orElse(false) ? 112 : 0);
        commands.setObject("#ServerKeymapList.Anchor", ControlPanelPage.height(server));
        commands.setObject("#KeymapList.Anchor", ControlPanelPage.height(admin ? 538 : 580));
    }
}
