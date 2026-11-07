package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.server.BackupsView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class BackupsTab {

    private static final String NOW = "BackupNow";
    private static final String SET_DIRECTORY = "BackupSetDirectory";
    private static final String CANCEL_RESTORE = "BackupCancelRestore";
    private static final String RESTORE = "BackupRestore";
    private static final String DELETE = "BackupDelete";
    private static final String LIST = "#BackupsList";
    private static final String ROW = "Pages/ControlCore/BackupRow.ui";
    private static final long CONFIRM_MILLIS = 10_000;

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void whileOpen(Runnable work);

        void whileOpenOrTell(Runnable work, String closedText);
    }

    private final BackupsView view;
    private final Translator tr;
    private final Host host;
    private final ConfirmGate confirm = new ConfirmGate(CONFIRM_MILLIS);

    BackupsTab(BackupsView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        boolean mayChange = view.mayChangeDirectory();
        String setup = view.configured()
                ? mayChange ? "controlcore.panel.backups.directory" : "controlcore.panel.backups.directory_read_only"
                : mayChange ? "controlcore.panel.backups.setup" : "controlcore.panel.backups.setup_ask";
        commands.set("#BackupsSetupText.Text", tr.t(setup));
        commands.set("#BackupsDirectory.Value", view.configured() ? view.directory() : "backups");
        commands.set("#BackupsStatus.Text", view.status());
        commands.set("#BackupsSettings.Text", view.settings());
        boolean busy = view.running() || view.preparingRestore();
        commands.set("#BackupsNow.Disabled", !view.configured() || busy);
        commands.set("#BackupsDirectory.Visible", mayChange);
        commands.set("#BackupsSetDirectory.Visible", mayChange);
        commands.set("#BackupsSetDirectory.Disabled", view.running());
        commands.set("#BackupsDirectoryNote.Visible", !mayChange && view.configured());
        commands.set("#BackupsDirectoryNote.Text", view.directory());
        Optional<String> pending = view.pending();
        commands.set("#BackupsPendingRow.Visible", pending.isPresent());
        commands.set("#BackupsPendingText.Text", pending.orElse(""));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackupsNow", EventData.of(PanelAction.KEY, NOW));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackupsSetDirectory",
                new EventData()
                        .append(PanelAction.KEY, SET_DIRECTORY)
                        .append(PanelAction.VALUE, "#BackupsDirectory.Value"));
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackupsCancelRestore",
                EventData.of(PanelAction.KEY, CANCEL_RESTORE));
        List<BackupsView.Row> rows = view.backups();
        commands.set("#BackupsEmpty.Visible", view.configured() && rows.isEmpty());
        for (int index = 0; index < rows.size(); index++) {
            BackupsView.Row backup = rows.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", backup.name());
            commands.set(row + " #Info.Text", backup.info());
            commands.set(row + " #PendingBadge.Visible", backup.pending());
            commands.set(row + " #Restore.Visible", pending.isEmpty() && !view.preparingRestore());
            for (String[] button : List.of(new String[] {"#Restore", RESTORE}, new String[] {"#Delete", DELETE})) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " " + button[0],
                        new EventData().append(PanelAction.KEY, button[1]).append(PanelAction.ID, backup.name()));
            }
        }
        confirm.disarm();
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case NOW -> backUp();
            case SET_DIRECTORY -> refreshAfter(view.setDirectory(action.value));
            case CANCEL_RESTORE -> refreshAfter(view.cancelRestore());
            case RESTORE -> {
                if (confirmed(RESTORE, action.id, "controlcore.panel.backups.confirm_restore")) {
                    restore(action.id);
                }
            }
            case DELETE -> {
                if (confirmed(DELETE, action.id, "controlcore.panel.backups.confirm_delete")) {
                    refreshAfter(view.delete(action.id));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void backUp() {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#BackupsNow.Disabled", true);
        update.set("#BackupsSetDirectory.Disabled", true);
        update.set("#BackupsStatus.Text", tr.t("controlcore.backups.running"));
        update.set("#FooterStatus.Text", tr.t("controlcore.backups.running"));
        host.send(update);
        view.backUpNow().thenAccept(result -> host.whileOpenOrTell(() -> refreshAfter(result), text(result)));
    }

    private void restore(String name) {
        CompletableFuture<Result<String>> planned = view.restoreOnNextStart(name);
        if (!planned.isDone()) {
            UICommandBuilder update = new UICommandBuilder();
            update.set("#BackupsNow.Disabled", true);
            update.set("#BackupsStatus.Text", tr.t("controlcore.backups.preparing_restore"));
            update.set("#FooterStatus.Text", tr.t("controlcore.backups.preparing_restore"));
            host.send(update);
        }
        planned.thenAccept(result -> host.whileOpenOrTell(() -> refreshAfter(result), text(result)));
    }

    private boolean confirmed(String kind, String name, String confirmKey) {
        if (confirm.confirmed(kind + ":" + name)) {
            return true;
        }
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", tr.t(confirmKey, "name", name));
        host.send(update);
        return false;
    }

    private void refreshAfter(Result<String> result) {
        host.rebuild();
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", text(result));
        host.send(update);
    }

    private String text(Result<String> result) {
        return result.isSuccess() ? result.value() : tr.error(result.error());
    }
}
