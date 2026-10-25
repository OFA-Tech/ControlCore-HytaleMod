package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.ofatech.controlcore.api.ActionContext;
import net.ofatech.controlcore.api.AutomationAction;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.automation.AutomationView;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutomationRuntimeTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void joinRulesGreetAndFirstJoinOnlyOnce() {
        save(form("Hello", "join", "", "none", "", "message-player", "Hi {player}, {online} online", "none", "", ""));
        save(form("New", "first-join", "", "none", "", "message-all", "Welcome {player}!", "none", "", ""));
        PlayerInfo bob = platform.players.join("Bob");

        platform.connectedPublisher.publish(new PlayerJoined(bob));

        assertThat(platform.messenger.textsTo(bob.id())).contains("Hi Bob, 2 online");
        assertThat(platform.messenger.broadcasts).containsExactly("Welcome Bob!");

        platform.players.leave(bob);
        platform.connectedPublisher.publish(new PlayerLeft(bob));
        platform.players.join("Bob");
        platform.connectedPublisher.publish(new PlayerJoined(bob));

        assertThat(platform.messenger.broadcasts).hasSize(1);
        assertThat(ControlCoreApi.get().sessions().find(bob.id())).hasValueSatisfying(session -> {
            assertThat(session.name()).isEqualTo("Bob");
            assertThat(session.online()).isTrue();
        });
    }

    @Test
    void timersWaitForTheirPeriod() {
        save(form(
                "Vote",
                "every-minutes",
                "10",
                "none",
                "",
                "message-all",
                "Vote for us!",
                "title-all",
                "Vote | now",
                ""));

        platform.scheduler.runAll();
        assertThat(platform.messenger.broadcasts).isEmpty();

        platform.clock.now = platform.clock.now.plus(Duration.ofMinutes(10));
        platform.scheduler.runAll();

        assertThat(platform.messenger.broadcasts).containsExactly("Vote for us!");
        assertThat(platform.messenger.titles).containsExactly("all: Vote | now");
    }

    @Test
    void conditionsAndCooldownsFilterRuns() {
        save(form("Staff", "join", "", "has-permission", "staff", "message-player", "Hi staff", "none", "", "120"));
        PlayerInfo bob = platform.players.join("Bob");

        platform.deniedNodes.add("staff");
        platform.connectedPublisher.publish(new PlayerJoined(bob));
        assertThat(platform.messenger.textsTo(bob.id())).doesNotContain("Hi staff");

        platform.deniedNodes.remove("staff");
        platform.players.leave(bob);
        platform.connectedPublisher.publish(new PlayerLeft(bob));
        platform.connectedPublisher.publish(new PlayerJoined(bob));
        platform.connectedPublisher.publish(new PlayerLeft(bob));
        platform.connectedPublisher.publish(new PlayerJoined(bob));

        assertThat(platform.messenger.textsTo(bob.id()).stream().filter("Hi staff"::equals))
                .hasSize(1);
    }

    @Test
    void settingActionsAreRecordedAndDoNotLoop() {
        save(form(
                "Echo",
                "setting-changed",
                WELCOME,
                "none",
                "",
                "set-setting",
                WELCOME + "=Changed: {value}",
                "none",
                "",
                "0"));

        run("cc-set", Map.of("id", WELCOME, "value", "Hi"));

        assertThat(value(WELCOME)).isEqualTo("Changed: Hi");
        assertThat(platform.changeLog.records)
                .filteredOn(record -> record.source() == ChangeRecord.Source.AUTOMATION)
                .singleElement()
                .satisfies(record -> assertThat(record.actor().name()).contains("Echo"));
    }

    @Test
    void modsOfferActionsAndTriggers() {
        AtomicReference<ActionContext> received = new AtomicReference<>();
        ControlCoreApi api = ControlCoreApi.get();
        api.actions()
                .register(AutomationAction.of("arena", "reward")
                        .displayName("Reward")
                        .run(received::set));
        api.actions().registerTrigger("arena", "won", "A match is won");
        save(form("Win", "mod-trigger", "arena:won", "none", "", "mod-action", "arena:reward {arena}", "none", "", ""));

        api.actions().fire("arena", "won", Optional.of(admin.id()), Map.of("arena", "Lava"));

        assertThat(received.get().value()).isEqualTo("Lava");
        assertThat(received.get().player()).contains(admin.id());
        assertThat(reply("cc-rules", Map.of()))
                .contains("arena:reward")
                .contains("arena:won")
                .contains("win");
    }

    @Test
    void badFormsAreRefusedAndCommandsManageRules() {
        assertThat(view().save(Optional.empty(), form("", "join", "", "none", "", "message-all", "x", "none", "", ""))
                        .error()
                        .code())
                .isEqualTo("Automation.InvalidName");
        assertThat(view().save(
                                Optional.empty(),
                                form("T", "daily-at", "25:00", "none", "", "message-all", "x", "none", "", ""))
                        .error()
                        .code())
                .isEqualTo("Automation.InvalidTriggerValue");
        assertThat(view().save(Optional.empty(), form("T", "join", "", "none", "", "profile", "@nope", "none", "", ""))
                        .error()
                        .code())
                .isEqualTo("Automation.InvalidActionValue");
        save(form("Ping", "join", "", "none", "", "message-all", "ping", "none", "", ""));

        assertThat(reply("cc-rule-test", Map.of("rule", "ping"))).contains("1");
        assertThat(platform.messenger.broadcasts).containsExactly("ping");
        assertThat(reply("cc-rule-toggle", Map.of("rule", "ping"))).contains("off");
        platform.connectedPublisher.publish(new PlayerJoined(platform.players.join("Eve")));
        assertThat(platform.messenger.broadcasts).hasSize(1);
        assertThat(reply("cc-rule-remove", Map.of("rule", "ping"))).contains("Ping");
        assertThat(platform.automationRules.rules).isEmpty();
    }

    @Test
    void theGlobalSwitchStopsEverything() {
        save(form("Ping", "join", "", "none", "", "message-all", "ping", "none", "", ""));
        run("cc-set", Map.of("id", "controlcore:automation-enabled", "value", "false"));

        platform.connectedPublisher.publish(new PlayerJoined(platform.players.join("Eve")));

        assertThat(platform.messenger.broadcasts).isEmpty();
    }

    private void save(AutomationView.Form form) {
        Result<String> saved = view().save(Optional.empty(), form);
        assertThat(saved.isSuccess()).as(saved.toString()).isTrue();
    }

    private AutomationView view() {
        run("open-admin-config", Map.of());
        return platform.controlPanel.lastContent.automation().orElseThrow();
    }

    private static AutomationView.Form form(String... values) {
        List<String> v = List.of(values);
        return new AutomationView.Form(
                v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), v.get(5), v.get(6), v.get(7), v.get(8), v.get(9));
    }

    private Result<Reply> run(String command, Map<String, Object> args) {
        return platform.commands.run(command, admin, args);
    }

    private String reply(String command, Map<String, Object> args) {
        var result = run(command, args);
        assertThat(result.isSuccess()).as(command + " " + args + ": " + result).isTrue();
        return result.value().text().orElse("");
    }

    private String value(String id) {
        return ControlCoreApi.get()
                .settings()
                .read(SettingId.of(id), Optional.empty())
                .value()
                .value();
    }
}
