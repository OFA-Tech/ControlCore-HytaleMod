package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.ofatech.controlcore.api.RulesApi;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.automation.ActionKind;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.automation.ConditionKind;
import net.ofatech.controlcore.domain.model.automation.Rule;
import net.ofatech.controlcore.domain.model.automation.Trigger;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(RulesApi.class)
public final class RulesApiService implements RulesApi {

    private final AutomationService automation;
    private final Players players;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public RulesApiService(AutomationService automation, Players players, Texts texts, PlayerDirectory directory) {
        this(automation, players, texts, directory, ApiActor.MOD);
    }

    private RulesApiService(
            AutomationService automation, Players players, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.automation = automation;
        this.players = players;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public RulesApi actingFor(PlayerId player) {
        return new RulesApiService(automation, players, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Entry> list() {
        if (!automation.mayManage(actor.who())) {
            return List.of();
        }
        return automation.entries(tr()).stream()
                .map(entry -> new Entry(entry.id(), entry.name(), entry.enabled(), entry.summary(), entry.lastRun()))
                .toList();
    }

    @Override
    public Optional<Form> get(String id) {
        if (!automation.mayManage(actor.who())) {
            return Optional.empty();
        }
        return automation
                .find(id)
                .map(AutomationService::form)
                .map(form -> new Form(
                        form.name(),
                        form.trigger(),
                        form.triggerValue(),
                        form.condition(),
                        form.conditionValue(),
                        form.actions().stream()
                                .map(step -> new Step(step.kind(), step.value()))
                                .toList(),
                        form.cooldown()));
    }

    @Override
    public Result<String> save(Optional<String> editing, Form form) {
        Set<String> before = automation.rules().stream().map(Rule::id).collect(Collectors.toSet());
        List<AutomationView.Step> steps = new ArrayList<>();
        if (form != null && form.actions() != null) {
            form.actions().forEach(step -> steps.add(new AutomationView.Step(step.kind(), step.value())));
        }
        AutomationView.Form written = form == null
                ? new AutomationView.Form("", "", "", "none", "", steps, "")
                : new AutomationView.Form(
                        text(form.name()),
                        text(form.trigger()),
                        text(form.triggerValue()),
                        form.condition() == null || form.condition().isBlank() ? "none" : form.condition(),
                        text(form.conditionValue()),
                        steps,
                        text(form.cooldown()));
        return actor.run(() -> automation.save(editing, written, actor.who(), tr()))
                .map(saved -> editing.orElseGet(() -> automation.rules().stream()
                        .map(Rule::id)
                        .filter(id -> !before.contains(id))
                        .findFirst()
                        .orElse("")));
    }

    @Override
    public Result<String> toggle(String id) {
        return actor.run(() -> automation.toggle(id, actor.who(), tr()));
    }

    @Override
    public Result<String> test(String id) {
        return actor.run(() -> automation.test(id, actor.who().flatMap(players::find), actor.who(), tr()));
    }

    @Override
    public Result<String> remove(String id) {
        return actor.run(() -> automation.remove(id, actor.who(), tr()));
    }

    @Override
    public List<String> triggers() {
        return Arrays.stream(Trigger.values()).map(Trigger::id).toList();
    }

    @Override
    public List<String> conditions() {
        List<String> ids = new ArrayList<>(List.of("none"));
        Arrays.stream(ConditionKind.values()).map(ConditionKind::id).forEach(ids::add);
        return List.copyOf(ids);
    }

    @Override
    public List<String> actions() {
        return Arrays.stream(ActionKind.values()).map(ActionKind::id).toList();
    }

    @Override
    public Map<String, String> modTriggers() {
        return Map.copyOf(automation.modTriggers());
    }

    @Override
    public Map<String, String> modActions() {
        return Map.copyOf(automation.modActions());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }
}
