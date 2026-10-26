package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class AutomationTab {

    static final int LIST_HEIGHT = 560;
    private static final int FORM_BASE = 300;
    private static final int STEP_HEIGHT = 42;

    private static final String NEW = "RuleNew";
    private static final String SAVE = "RuleSave";
    private static final String CLOSE = "RuleFormClose";
    private static final String TOGGLE = "RuleToggle";
    private static final String TEST = "RuleTest";
    private static final String EDIT = "RuleEdit";
    private static final String REMOVE = "RuleRemove";
    private static final String PICKED = "RulePicked";
    private static final String ADD_STEP = "RuleStepAdd";
    private static final String STEP_UP = "RuleStepUp";
    private static final String STEP_DOWN = "RuleStepDown";
    private static final String STEP_REMOVE = "RuleStepRemove";
    private static final String LIST = "#RuleList";
    private static final String STEPS = "#RuleActions";
    private static final String ROW = "Pages/ControlCore/RuleRow.ui";
    private static final String STEP_ROW = "Pages/ControlCore/RuleActionRow.ui";
    private static final String ICONS = "Pages/ControlCore/Icons/";
    private static final String[] FIELDS = {"Name", "Trigger", "TriggerValue", "Condition", "ConditionValue", "Cooldown"
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleNew", EventData.of(PanelAction.KEY, NEW));
        events.addEventBinding(
                CustomUIEventBindingType.Activating, "#RuleFormClose", EventData.of(PanelAction.KEY, CLOSE));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleSave", capture(SAVE, -1));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RuleActionAdd", capture(ADD_STEP, -1));
        for (String dropdown : List.of("Trigger", "Condition")) {
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    "#Rule" + dropdown,
                    new EventData()
                            .append(PanelAction.KEY, PICKED)
                            .append(PanelAction.ITEM, dropdown)
                            .append(PanelAction.VALUE, "#Rule" + dropdown + ".Value"));
        }
        buildSteps(commands, events);
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

    private void buildSteps(UICommandBuilder commands, UIEventBuilder events) {
        List<DropdownEntryInfo> kinds = options("action", ids(ActionKind.values()), false);
        List<AutomationView.Step> steps = draft.actions();
        for (int index = 0; index < steps.size(); index++) {
            AutomationView.Step step = steps.get(index);
            String row = STEPS + "[" + index + "]";
            commands.append(STEPS, STEP_ROW);
            commands.set(
                    row + " #Number.Text",
                    index == 0
                            ? tr.t("controlcore.panel.automation.step_first")
                            : tr.t("controlcore.panel.automation.step", "number", index + 1));
            commands.set(row + " #Kind.Entries", kinds);
            commands.set(row + " #Kind.Value", step.kind());
            commands.set(row + " #Value.Value", step.value());
            commands.set(row + " #Value.PlaceholderText", view.hint("action", step.kind()));
            commands.set(row + " #Up.Visible", index > 0);
            commands.set(row + " #Down.Visible", index < steps.size() - 1);
            commands.set(row + " #Remove.Visible", steps.size() > 1);
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    row + " #Kind",
                    new EventData()
                            .append(PanelAction.KEY, PICKED)
                            .append(PanelAction.ITEM, Integer.toString(index))
                            .append(PanelAction.VALUE, row + " #Kind.Value"));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Up", capture(STEP_UP, index));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Down", capture(STEP_DOWN, index));
            events.addEventBinding(CustomUIEventBindingType.Activating, row + " #Remove", capture(STEP_REMOVE, index));
        }
        commands.set("#RuleActionAdd.Visible", steps.size() < AutomationService.MAX_ACTIONS);
    }

    private EventData capture(String key, int index) {
        EventData data = new EventData().append(PanelAction.KEY, key);
        if (index >= 0) {
            data.append(PanelAction.ID, Integer.toString(index));
        }
        for (String field : FIELDS) {
            data.append(PanelAction.ruleKey(field), "#Rule" + field + ".Value");
        }
        for (int i = 0; i < draft.actions().size(); i++) {
            String row = STEPS + "[" + i + "]";
            data.append(PanelAction.ruleKey("A" + i + "Kind"), row + " #Kind.Value");
            data.append(PanelAction.ruleKey("A" + i + "Value"), row + " #Value.Value");
        }
        return data;
    }

    boolean handle(PanelAction action) {
        switch (action.name()) {
            case NEW -> {
                editing = Optional.empty();
                draft = blank();
                formOpen = true;
                host.rebuild();
            }
            case CLOSE -> {
                formOpen = false;
                UICommandBuilder update = new UICommandBuilder();
                showForm(update);
                host.send(update);
            }
            case EDIT -> {
                Optional<AutomationView.Form> form = view.form(action.id);
                if (form.isPresent()) {
                    editing = Optional.of(action.id);
                    draft = form.get();
                    formOpen = true;
                    host.rebuild();
                } else {
                    host.send(new UICommandBuilder());
                }
            }
            case PICKED -> picked(action);
            case ADD_STEP, STEP_UP, STEP_DOWN, STEP_REMOVE -> changeSteps(action);
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

    private void picked(PanelAction action) {
        UICommandBuilder update = new UICommandBuilder();
        String item = action.item == null ? "" : action.item;
        String value = action.value == null ? "" : action.value;
        if (item.matches("\\d+")) {
            update.set(STEPS + "[" + item + "] #Value.PlaceholderText", view.hint("action", value));
        } else {
            String kind = item.equals("Trigger") ? "trigger" : "condition";
            String hint = view.hint(kind, value);
            update.set("#Rule" + item + "Value.PlaceholderText", hint);
            update.set("#Rule" + item + "Value.Visible", !hint.isEmpty());
        }
        host.send(update);
    }

    private void changeSteps(PanelAction action) {
        draft = captured(action);
        List<AutomationView.Step> steps = new ArrayList<>(draft.actions());
        int index = action.id != null && action.id.matches("\\d+") ? Integer.parseInt(action.id) : -1;
        switch (action.name()) {
            case ADD_STEP -> {
                if (steps.size() < AutomationService.MAX_ACTIONS) {
                    steps.add(new AutomationView.Step("message-all", ""));
                }
            }
            case STEP_UP -> {
                if (index > 0 && index < steps.size()) {
                    steps.add(index - 1, steps.remove(index));
                }
            }
            case STEP_DOWN -> {
                if (index >= 0 && index < steps.size() - 1) {
                    steps.add(index + 1, steps.remove(index));
                }
            }
            default -> {
                if (index >= 0 && index < steps.size() && steps.size() > 1) {
                    steps.remove(index);
                }
            }
        }
        draft = withSteps(draft, steps);
        host.rebuild();
    }

    private void save(PanelAction action) {
        AutomationView.Form form = captured(action);
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

    private AutomationView.Form captured(PanelAction action) {
        List<AutomationView.Step> steps = new ArrayList<>();
        for (int i = 0; i < draft.actions().size(); i++) {
            String kind = action.rule("A" + i + "Kind");
            steps.add(new AutomationView.Step(
                    kind.isEmpty() ? draft.actions().get(i).kind() : kind, action.rule("A" + i + "Value")));
        }
        return new AutomationView.Form(
                action.rule("Name"),
                action.rule("Trigger"),
                action.rule("TriggerValue"),
                action.rule("Condition"),
                action.rule("ConditionValue"),
                steps,
                action.rule("Cooldown"));
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
            commands.set("#RuleCooldown.Value", draft.cooldown());
            commands.set("#RuleCooldown.PlaceholderText", tr.t("controlcore.panel.automation.cooldown_hint"));
            commands.set("#RuleName.PlaceholderText", tr.t("controlcore.panel.automation.name_hint"));
            hints(commands, "Trigger", "trigger", draft.trigger());
            hints(commands, "Condition", "condition", draft.condition());
        }
        int form = FORM_BASE + STEP_HEIGHT * draft.actions().size();
        host.resize(LIST, formOpen ? Math.max(120, LIST_HEIGHT - form) : LIST_HEIGHT, commands);
    }

    private void hints(UICommandBuilder commands, String field, String kind, String value) {
        String hint = view.hint(kind, value);
        commands.set("#Rule" + field + "Value.PlaceholderText", hint);
        commands.set("#Rule" + field + "Value.Visible", !hint.isEmpty());
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
            ids.add(value.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return ids;
    }

    private static AutomationView.Form withSteps(AutomationView.Form form, List<AutomationView.Step> steps) {
        return new AutomationView.Form(
                form.name(),
                form.trigger(),
                form.triggerValue(),
                form.condition(),
                form.conditionValue(),
                steps,
                form.cooldown());
    }

    private static AutomationView.Form blank() {
        return new AutomationView.Form(
                "", "join", "", "none", "", List.of(new AutomationView.Step("message-player", "")), "");
    }
}
