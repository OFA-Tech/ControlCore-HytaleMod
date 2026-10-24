package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBindEditor;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class KeyBindForm {

    static final String ADD = "AddBind";
    static final String EDIT = "EditBind";
    static final String REMOVE = "RemoveBind";

    private static final String STATUS = "#FooterStatus.Text";

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);
    }

    private final CustomKeyBindEditor binds;
    private final Translator tr;
    private final Host host;

    KeyBindForm(CustomKeyBindEditor binds, Translator tr, Host host) {
        this.binds = binds;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#NewBindName.PlaceholderText", tr.t("controlcore.panel.bind.name_hint"));
        commands.set("#NewBindKeys.PlaceholderText", tr.t("controlcore.panel.bind.keys_hint"));
        commands.set("#NewBindCommand.PlaceholderText", tr.t("controlcore.panel.bind.command_hint"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NewBindAdd",
                new EventData()
                        .append(PanelAction.KEY, ADD)
                        .append(PanelAction.NAME, "#NewBindName.Value")
                        .append(PanelAction.KEYS, "#NewBindKeys.Value")
                        .append(PanelAction.VALUE, "#NewBindCommand.Value")
                        .append(PanelAction.CHECKED, "#NewBindAsServer.Value"));
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case ADD -> add(action);
            case EDIT -> edit(action.id);
            case REMOVE -> remove(action.id);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void edit(String id) {
        if (id == null) {
            return;
        }
        UICommandBuilder update = new UICommandBuilder();
        binds.find(id)
                .ifPresentOrElse(
                        bind -> {
                            update.set("#NewBindName.Value", bind.name());
                            update.set("#NewBindKeys.Value", bind.keys());
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
        Result<String> result =
                binds.add(action.bindName, action.bindKeys, action.value, Boolean.TRUE.equals(action.checked));
        if (result.isSuccess()) {
            host.rebuild();
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set(STATUS, result.isSuccess() ? result.value() : tr.error(result.error()));
        if (result.isSuccess()) {
            update.set("#NewBindName.Value", "");
            update.set("#NewBindKeys.Value", "");
            update.set("#NewBindCommand.Value", "");
            update.set("#NewBindAsServer.Value", false);
        }
        host.send(update);
    }
}
