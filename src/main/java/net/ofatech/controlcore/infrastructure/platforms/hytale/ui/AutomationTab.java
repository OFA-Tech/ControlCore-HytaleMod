package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class AutomationTab {

    static final int LIST_HEIGHT = 560;
    static final int FORM_HEIGHT = 300;

    private static final String NEW = "RuleNew";
    private static final String SAVE = "RuleSave";
    private static final String CLOSE = "RuleFormClose";
    private static final String TOGGLE = "RuleToggle";
    private static final String TEST = "RuleTest";
    private static final String EDIT = "RuleEdit";
    private static final String REMOVE = "RuleRemove";
    private static final String PICKED = "RulePicked";
    private static final String LIST = "#RuleList";
    private static final String ROW = "Pages/ControlCore/RuleRow.ui";
    private static final String ICONS = "Pages/ControlCore/Icons/";
    private static final String[] FIELDS = {
        "Name",
        "Trigger",
        "TriggerValue",
        "Condition",
        "ConditionValue",
        "Action",
        "ActionValue",
        "Action2",
        "Action2Value",
        "Cooldown"
    };

    interface Host {
        void rebuild();

        void send(UICommandBuilder update);

        void showRestartNotice(UICommandBuilder update);

        void resize(String element, int height, UICommandBuilder update);
    }

    private final AutomationView view;
    private final Translator tr;
    private final Host host;
    private boolean formOpen;
    private Optional<String> editing = Optional.empty();
    private AutomationView.Form draft = blank();
    private Optional<String> armedRemove = Optional.empty();

    AutomationTab(AutomationView view, Translator tr, Host host) {
        this.view = view;
        this.tr = tr;
        this.host = host;
    }

    void build(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#AutomationOff.Visible", !view.globallyOn());
        commands.set("#AutomationOffText.Text", tr.t("controlcore.panel.automation.globally_off"));
        commands.set("#RuleTrigger.Entries", options("trigger", ids(Trigger.values()), false));
        commands.set("#RuleCondition.Entries", options("condition", ids(ConditionKind.values()), true));
        commands.set("#RuleAction.Entries", options("action", ids(ActionKind.values()), false));
        commands.set("#RuleAction2.Entries", options("action", ids(ActionKind.values()), true));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleNew", EventData.of(PanelAction.KEY, NEW));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#RuleFormClose", EventData.of(PanelAction.KEY, CLOSE));
        EventData save = new EventData().append(PanelAction.KEY, SAVE);
        for (String field : FIELDS) {
            save.append(PanelAction.ruleKey(field), "#Rule" + field + ".Value");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleSave", save);
        for (String dropdown : List.of("Trigger", "Condition", "Action", "Action2")) {
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    "#Rule" + dropdown,
                    new EventData()
                            .append(PanelAction.KEY, PICKED)
                            .append(PanelAction.ITEM, dropdown)
                            .append(PanelAction.VALUE, "#Rule" + dropdown + ".Value"));
        }
        List<AutomationView.Entry> entries = view.list();
        commands.set("#RuleEmpty.Visible", entries.isEmpty());
        for (int index = 0; index < entries.size(); index++) {
            AutomationView.Entry entry = entries.get(index);
            String row = LIST + "[" + index + "]";
            commands.append(LIST, ROW);
            commands.set(row + " #Name.Text", entry.name());
            commands.set(row + " #Details.Text", entry.summary() + " · " + entry.lastRun());
            commands.set(row + " #OnBadge.Visible", entry.enabled());
            commands.set(row + " #OffBadge.Visible", !entry.enabled());
            commands.set(row + " #State.Background", ICONS + (entry.enabled() ? "Changed.png" : "Default.png"));
            commands.set(
                    row + " #Toggle.Text",
                    tr.t(
                            entry.enabled()
                                    ? "controlcore.panel.automation.turn_off"
                                    : "controlcore.panel.automation.turn_on"));
            for (String[] button : List.of(
                    new String[] {"#Toggle", TOGGLE},
                    new String[] {"#Test", TEST},
                    new String[] {"#Edit", EDIT},
                    new String[] {"#Remove", REMOVE})) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " " + button[0],
                        new EventData().append(PanelAction.KEY, button[1]).append(PanelAction.ID, entry.id()));
            }
        }
        armedRemove = Optional.empty();
        showForm(commands);
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case NEW -> {
                editing = Optional.empty();
                draft = blank();
                formOpen = true;
                UICommandBuilder update = new UICommandBuilder();
                showForm(update);
                host.send(update);
            }
            case CLOSE -> {
                formOpen = false;
                UICommandBuilder update = new UICommandBuilder();
                showForm(update);
                host.send(update);
            }
            case EDIT -> {
                Optional<AutomationView.Form> form = view.form(action.id);
                UICommandBuilder update = new UICommandBuilder();
                if (form.isPresent()) {
                    editing = Optional.of(action.id);
                    draft = form.get();
                    formOpen = true;
                    showForm(update);
                }
                host.send(update);
            }
            case PICKED -> {
                UICommandBuilder update = new UICommandBuilder();
                String field = action.item == null ? "" : action.item;
                String value = action.value == null ? "" : action.value;
                String kind = field.equals("Trigger") ? "trigger" : field.equals("Condition") ? "condition" : "action";
                update.set("#Rule" + field + "Value.PlaceholderText", view.hint(kind, value));
                update.set(
                        "#Rule" + field + "Value.Visible",
                        !view.hint(kind, value).isEmpty() || !kind.equals("trigger"));
                host.send(update);
            }
            case SAVE -> save(action);
            case TOGGLE -> refreshAfter(view.toggle(action.id));
            case TEST -> footer(view.test(action.id));
            case REMOVE -> remove(action.id);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void save(PanelAction action) {
        AutomationView.Form form = new AutomationView.Form(
                action.rule("Name"),
                action.rule("Trigger"),
                action.rule("TriggerValue"),
                action.rule("Condition"),
                action.rule("ConditionValue"),
                action.rule("Action"),
                action.rule("ActionValue"),
                action.rule("Action2"),
                action.rule("Action2Value"),
                action.rule("Cooldown"));
        draft = form;
        Result<String> result = view.save(editing, form);
        if (result.isSuccess()) {
            formOpen = false;
            editing = Optional.empty();
            draft = blank();
            host.rebuild();
        }
        footer(result);
    }

    private void remove(String id) {
        if (id == null) {
            host.send(new UICommandBuilder());
            return;
        }
        if (armedRemove.filter(id::equals).isEmpty()) {
            armedRemove = Optional.of(id);
            UICommandBuilder update = new UICommandBuilder();
            update.set("#FooterStatus.Text", tr.t("controlcore.panel.automation.confirm_remove"));
            host.send(update);
            return;
        }
        armedRemove = Optional.empty();
        refreshAfter(view.remove(id));
    }

    private void refreshAfter(Result<String> result) {
        if (result.isSuccess()) {
            host.rebuild();
        }
        footer(result);
    }

    private void footer(Result<String> result) {
        UICommandBuilder update = new UICommandBuilder();
        update.set("#FooterStatus.Text", result.isSuccess() ? result.value() : tr.error(result.error()));
        host.showRestartNotice(update);
        host.send(update);
    }

    private void showForm(UICommandBuilder commands) {
        commands.set("#RuleForm.Visible", formOpen);
        commands.set(
                "#RuleFormTitle.Text",
                tr.t(
                        editing.isPresent()
                                ? "controlcore.panel.automation.editing"
                                : "controlcore.panel.automation.new_rule"));
        commands.set("#RuleNew.Visible", !formOpen);
        if (formOpen) {
            commands.set("#RuleName.Value", draft.name());
            commands.set("#RuleTrigger.Value", draft.trigger());
            commands.set("#RuleTriggerValue.Value", draft.triggerValue());
            commands.set("#RuleCondition.Value", draft.condition());
            commands.set("#RuleConditionValue.Value", draft.conditionValue());
            commands.set("#RuleAction.Value", draft.action());
            commands.set("#RuleActionValue.Value", draft.actionValue());
            commands.set("#RuleAction2.Value", draft.action2());
            commands.set("#RuleAction2Value.Value", draft.action2Value());
            commands.set("#RuleCooldown.Value", draft.cooldown());
            commands.set("#RuleCooldown.PlaceholderText", tr.t("controlcore.panel.automation.cooldown_hint"));
            commands.set("#RuleName.PlaceholderText", tr.t("controlcore.panel.automation.name_hint"));
            hints(commands, "Trigger", "trigger", draft.trigger());
            hints(commands, "Condition", "condition", draft.condition());
            hints(commands, "Action", "action", draft.action());
            hints(commands, "Action2", "action", draft.action2());
        }
        host.resize(LIST, formOpen ? LIST_HEIGHT - FORM_HEIGHT : LIST_HEIGHT, commands);
    }

    private void hints(UICommandBuilder commands, String field, String kind, String value) {
        String hint = view.hint(kind, value);
        commands.set("#Rule" + field + "Value.PlaceholderText", hint);
        commands.set("#Rule" + field + "Value.Visible", !hint.isEmpty() || !kind.equals("trigger"));
    }

    private List<DropdownEntryInfo> options(String kind, List<String> ids, boolean withNone) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        if (withNone) {
            entries.add(new DropdownEntryInfo(
                    LocalizableString.fromString(tr.t("controlcore.automation." + kind + ".none")), "none"));
        }
        for (String id : ids) {
            entries.add(new DropdownEntryInfo(
                    LocalizableString.fromString(tr.t("controlcore.automation." + kind + "." + id)), id));
        }
        return entries;
    }

    private static List<String> ids(Enum<?>[] values) {
        List<String> ids = new ArrayList<>();
        for (Enum<?> value : values) {
            ids.add(value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        }
        return ids;
    }

    private static AutomationView.Form blank() {
        return new AutomationView.Form("", "join", "", "none", "", "message-player", "", "none", "", "");
    }
}
