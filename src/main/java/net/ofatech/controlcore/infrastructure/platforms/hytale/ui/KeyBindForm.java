package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import net.ofatech.controlcore.domain.result.Result;

final class KeyBindForm {

    static final String ADD = "AddBind";
    static final String EDIT = "EditBind";
    static final String REMOVE = "RemoveBind";
    static final String TOGGLE = "ToggleBindForm";
    static final String DETECT = "DetectBindKey";
    private static final Set<String> CHANGES = Set.of(ADD, EDIT, REMOVE, DETECT);

    private static final String STATUS = "#FooterStatus.Text";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void formToggled(UICommandBuilder update);

        void detect(BiFunction<String, KeyDetection.Mode, String> applyKeys);
    }

    private record Draft(String name, String command, boolean asServer) {}

    private final CustomKeyBindEditor binds;
    private final Translator tr;
    private final Host host;
    private final BooleanSupplier allKeys;
    private boolean open;
    private String formKeys = KeyBinds.NONE;
    private Optional<Draft> draft = Optional.empty();

    KeyBindForm(CustomKeyBindEditor binds, Translator tr, Host host, BooleanSupplier allKeys) {
        this.allKeys = allKeys;
        this.binds = binds;
        this.tr = tr;
        this.host = host;
    }

    boolean isOpen() {
        return open;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        show(commands);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#NewBindToggle", EventData.of(PanelAction.KEY, TOGGLE));
        commands.set("#NewBindName.PlaceholderText", tr.t("controlcore.panel.bind.name_hint"));
        SettingRows.showKeys(tr, "#NewBindHold", "#NewBindKey", formKeys, allKeys.getAsBoolean(), commands);
        commands.set("#NewBindCommand.PlaceholderText", tr.t("controlcore.panel.bind.command_hint"));
        draft.ifPresent(saved -> {
            commands.set("#NewBindName.Value", saved.name());
            commands.set("#NewBindCommand.Value", saved.command());
            commands.set("#NewBindAsServer.Value", saved.asServer());
        });
        draft = Optional.empty();
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NewBindAdd", fields(ADD));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NewBindDetect", fields(DETECT));
    }

    private static EventData fields(String key) {
        return new EventData()
                .append(PanelAction.KEY, key)
                .append(PanelAction.NAME, "#NewBindName.Value")
                .append(PanelAction.KEYS, "#NewBindKey.Value")
                .append(PanelAction.EDIT, "#NewBindHold.Value")
                .append(PanelAction.VALUE, "#NewBindCommand.Value")
                .append(PanelAction.CHECKED, "#NewBindAsServer.Value");
    }

    static boolean changesServerBinds(String name) {
        return CHANGES.contains(name);
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case ADD -> add(action);
            case DETECT -> detect(action);
            case EDIT -> edit(action.id);
            case REMOVE -> remove(action.id);
            case TOGGLE -> {
                open = !open;
                if (!open) {
                    clearDraft();
                }
                UICommandBuilder update = new UICommandBuilder();
                show(update);
                host.formToggled(update);
                host.send(update);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void show(UICommandBuilder commands) {
        commands.set("#CustomBindForm.Visible", open);
        commands.set(
                "#NewBindToggle.Text", open ? tr.t("controlcore.panel.bind.close") : tr.t("controlcore.ui.bind.new"));
    }

    private void edit(String id) {
        if (id == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        open = true;
        show(update);
        host.formToggled(update);
        binds.find(id)
                .ifPresentOrElse(
                        bind -> {
                            draft = Optional.empty();
                            formKeys = bind.keys();
                            update.set("#NewBindName.Value", bind.name());
                            SettingRows.showKeys(
                                    tr, "#NewBindHold", "#NewBindKey", bind.keys(), allKeys.getAsBoolean(), update);
                            update.set("#NewBindCommand.Value", bind.command());
                            update.set("#NewBindAsServer.Value", bind.runAsServer());
                            update.set(STATUS, tr.t("controlcore.panel.bind.editing", "name", bind.name()));
                        },
                        () -> update.set(STATUS, tr.t("controlcore.panel.bind.unknown", "id", id)));
        host.send(update);
    }

    private void remove(String id) {
        if (id == null) {
            return;
        }
        Result<String> result = binds.remove(id);
        if (result.isSuccess()) {
            host.rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(STATUS, result.isSuccess() ? result.value() : tr.error(result.error()));
        host.send(update);
    }

    private void add(PanelAction action) {
        String keys = KeyPicker.compose(action.edit, action.bindKeys, formKeys);
        Result<String> result = binds.add(action.bindName, keys, action.value, Boolean.TRUE.equals(action.checked));
        if (result.isSuccess()) {
            host.rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(STATUS, result.isSuccess() ? result.value() : tr.error(result.error()));
        if (result.isSuccess()) {
            clearDraft();
            update.set("#NewBindName.Value", "");
            SettingRows.showKeys(tr, "#NewBindHold", "#NewBindKey", "none", allKeys.getAsBoolean(), update);
            update.set("#NewBindCommand.Value", "");
            update.set("#NewBindAsServer.Value", false);
        }
        host.send(update);
    }

    private void detect(PanelAction action) {
        open = true;
        formKeys = KeyPicker.compose(action.edit, action.bindKeys, formKeys);
        Draft typed = new Draft(
                action.bindName == null ? "" : action.bindName,
                action.value == null ? "" : action.value,
                Boolean.TRUE.equals(action.checked));
        draft = Optional.of(typed);
        host.detect((keys, mode) -> {
            formKeys = KeyPicker.withDetected(keys, formKeys);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#NewBindName.Value", typed.name());
            update.set("#NewBindCommand.Value", typed.command());
            update.set("#NewBindAsServer.Value", typed.asServer());
            SettingRows.showKeys(tr, "#NewBindHold", "#NewBindKey", formKeys, allKeys.getAsBoolean(), update);
            host.send(update);
            String text = tr.t("controlcore.keys.detect.filled", "keys", KeyPicker.describe(tr, keys));
            return KeyPicker.heard(keys) ? text : text + " " + tr.t("controlcore.keys.detect.unheard");
        });
    }

    private void clearDraft() {
        draft = Optional.empty();
        formKeys = KeyBinds.NONE;
    }
}
