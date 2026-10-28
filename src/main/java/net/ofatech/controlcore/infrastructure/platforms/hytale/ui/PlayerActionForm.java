package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class PlayerActionForm {

    static final String FORM = "#PlayerActionForm";
    private static final String OPEN = "PlayerActionNew";
    private static final String CLOSE = "PlayerActionClose";
    private static final String KIND = "PlayerActionKind";
    private static final String GROUP = "PlayerActionGroup";
    private static final String FIELD = "PlayerActionField";
    private static final String APPLY = "PlayerActionApply";
    private static final String PERMANENT = "permanent";
    private static final List<String> TEXT_FIELDS = List.of("Name", "Node", "Reason");

    private final PlayersView view;
    private final Translator tr;
    private boolean open;
    private boolean busy;
    private PlayerAction action = PlayerAction.BAN;
    private String player = "";
    private String node = "";
    private String group = "";
    private String length = PERMANENT;
    private String reason = "";

    PlayerActionForm(PlayersView view, Translator tr) {
        this.view = view;
        this.tr = tr;
    }

    boolean allowed() {
        return !PlayerAction.allowedBy(view.rights()).isEmpty();
    }

    boolean isOpen() {
        return open && allowed();
    }

    void prefillBan(String name) {
        open = true;
        action = PlayerAction.BAN;
        player = name;
        reason = "";
        length = PERMANENT;
    }

    String kickReason() {
        return isOpen() && action == PlayerAction.KICK ? reason : "";
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        List<PlayerAction> actions = PlayerAction.allowedBy(view.rights());
        if (!actions.isEmpty() && !actions.contains(action)) {
            action = actions.getFirst();
        }
        commands.set(
                "#PlayerActionKind.Entries",
                PlayersTab.entries(actions.stream()
                        .map(found ->
                                new PlayersView.Choice(found.id(), tr.t("controlcore.players.action." + found.id())))
                        .toList()));
        commands.set("#PlayerActionKind.Value", action.id());
        List<PlayersView.Choice> groups = view.groups();
        if (groups.stream().noneMatch(choice -> choice.id().equals(group))) {
            group = groups.isEmpty() ? "" : groups.getFirst().id();
        }
        commands.set("#PlayerActionGroup.Entries", PlayersTab.entries(groups));
        if (!group.isEmpty()) {
            commands.set("#PlayerActionGroup.Value", group);
        }
        commands.set("#PlayerActionLength.Entries", PlayersTab.entries(view.lengths()));
        commands.set("#PlayerActionLength.Value", length);
        commands.set("#PlayerActionPlayer.Value", player);
        commands.set("#PlayerActionPlayer.PlaceholderText", tr.t("controlcore.panel.players.player_hint"));
        commands.set("#PlayerActionNode.Value", node);
        commands.set("#PlayerActionNode.PlaceholderText", tr.t("controlcore.players.node_field_hint"));
        commands.set("#PlayerActionReason.Value", reason);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#PlayerActionNew", EventData.of(PanelAction.KEY, OPEN));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#PlayerActionClose", EventData.of(PanelAction.KEY, CLOSE));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerActionKind",
                new EventData().append(PanelAction.KEY, KIND).append(PanelAction.VALUE, "#PlayerActionKind.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerActionGroup",
                new EventData().append(PanelAction.KEY, GROUP).append(PanelAction.VALUE, "#PlayerActionGroup.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#PlayerActionLength",
                new EventData()
                        .append(PanelAction.KEY, FIELD)
                        .append(PanelAction.ITEM, "Length")
                        .append(PanelAction.VALUE, "#PlayerActionLength.Value"));
        for (String field : TEXT_FIELDS) {
            String element = "#PlayerAction" + ("Name".equals(field) ? "Player" : field);
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    element,
                    new EventData()
                            .append(PanelAction.KEY, FIELD)
                            .append(PanelAction.ITEM, field)
                            .append(PanelAction.VALUE, element + ".Value"));
        }
        EventData apply = new EventData()
                .append(PanelAction.KEY, APPLY)
                .append(PanelAction.playerKey("Kind"), "#PlayerActionKind.Value")
                .append(PanelAction.playerKey("Group"), "#PlayerActionGroup.Value")
                .append(PanelAction.playerKey("Name"), "#PlayerActionPlayer.Value")
                .append(PanelAction.playerKey("Node"), "#PlayerActionNode.Value")
                .append(PanelAction.playerKey("Length"), "#PlayerActionLength.Value")
                .append(PanelAction.playerKey("Reason"), "#PlayerActionReason.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayerActionApply", apply);
        show(commands);
    }

    void show(UICommandBuilder commands) {
        boolean visible = isOpen();
        commands.set(FORM + ".Visible", visible);
        commands.set("#PlayerActionNew.Visible", allowed() && !visible);
        commands.set("#PlayerActionPlayer.Visible", action.needsPlayer());
        commands.set("#PlayerActionNode.Visible", action.needsNode());
        commands.set("#PlayerActionLength.Visible", action.needsLength());
        commands.set("#PlayerActionReason.Visible", action.needsReason());
        commands.set(
                "#PlayerActionReason.PlaceholderText",
                tr.t(
                        action == PlayerAction.KICK
                                ? "controlcore.players.kick_reason_hint"
                                : "controlcore.panel.players.reason_hint"));
        commands.set("#PlayerActionGroup.Visible", action.needsGroup());
        boolean note = action.needsNode() && !group.isEmpty();
        commands.set("#PlayerActionNote.Visible", note && visible);
        if (note) {
            List<String> nodes = view.groupPermissions(group);
            commands.set(
                    "#PlayerActionNote.Text",
                    nodes.isEmpty()
                            ? tr.t("controlcore.players.group_no_nodes")
                            : tr.t("controlcore.players.group_nodes", "nodes", String.join(", ", nodes)));
        }
    }

    boolean showsNote() {
        return isOpen() && action.needsNode() && !group.isEmpty();
    }

    boolean handle(
            PanelAction event, Consumer<UICommandBuilder> send, Consumer<CompletableFuture<Result<String>>> finish) {
        switch (event.name()) {
            case OPEN, CLOSE -> {
                open = OPEN.equals(event.name());
                UICommandBuilder update = new UICommandBuilder();
                show(update);
                send.accept(update);
            }
            case KIND -> {
                PlayerAction.of(text(event.value)).ifPresent(chosen -> action = chosen);
                UICommandBuilder update = new UICommandBuilder();
                show(update);
                send.accept(update);
            }
            case GROUP -> {
                group = text(event.value).isEmpty() ? group : text(event.value);
                UICommandBuilder update = new UICommandBuilder();
                show(update);
                send.accept(update);
            }
            case FIELD -> {
                remember(text(event.item), event.value == null ? "" : event.value);
                send.accept(new UICommandBuilder());
            }
            case APPLY -> apply(event, send, finish);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void remember(String field, String value) {
        switch (field) {
            case "Name" -> player = value;
            case "Node" -> node = value;
            case "Reason" -> reason = value;
            case "Length" -> length = value.isBlank() ? PERMANENT : value;
            default -> {}
        }
    }

    private void apply(
            PanelAction event, Consumer<UICommandBuilder> send, Consumer<CompletableFuture<Result<String>>> finish) {
        if (busy) {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.players.working"));
            send.accept(update);
            return;
        }
        PlayerAction.of(event.player("Kind")).ifPresent(chosen -> action = chosen);
        for (String field : List.of("Name", "Node", "Reason", "Length")) {
            String value = event.player(field);
            if (!value.isEmpty() || !"Length".equals(field)) {
                remember(field, value);
            }
        }
        if (!event.player("Group").isBlank()) {
            group = event.player("Group").strip();
        }
        if (!PlayerAction.allowedBy(view.rights()).contains(action)) {
            send.accept(new UICommandBuilder());
            return;
        }
        busy = true;
        CompletableFuture<Result<String>> pending =
                view.apply(action, new PlayerAction.Input(player.strip(), length, reason.strip(), group, node.strip()));
        finish.accept(pending);
    }

    void finished(Result<String> result) {
        busy = false;
        if (result.isSuccess()) {
            open = false;
            player = "";
            node = "";
            reason = "";
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
