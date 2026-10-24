package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeHistoryRuntimeTest extends RuntimeTestBase {

    private static final String WELCOME = "controlcore:welcome-message";

    private PlayerInfo admin;

    @BeforeEach
    void startUp() {
        runtime.setup();
        runtime.start();
        admin = platform.players.join("Admin");
    }

    @Test
    void aCommandChangeRecordsWhoWhenAndTheOldValue() {
        platform.commands.run("cc-set", admin, Map.of("id", WELCOME, "value", "Hello"));

        ChangeRecord record = last();
        assertThat(record.kind()).isEqualTo(ChangeRecord.Kind.SETTING);
        assertThat(record.target()).isEqualTo(WELCOME);
        assertThat(record.oldValue()).isEmpty();
        assertThat(record.newValue()).isEqualTo("Hello");
        assertThat(record.actor().player()).contains(admin.id());
        assertThat(record.actor().name()).isEqualTo("Admin");
        assertThat(record.source()).isEqualTo(ChangeRecord.Source.COMMAND);
        assertThat(record.at()).isEqualTo(platform.clock.now);
        assertThat(reply("cc-history", Map.of()))
                .contains("#" + record.id())
                .contains("Admin")
                .contains("Hello");
    }

    @Test
    void rollbackPutsTheOldValueBackOnce() {
        platform.commands.run("cc-set", admin, Map.of("id", WELCOME, "value", "Hello"));
        platform.commands.run("cc-set", admin, Map.of("id", WELCOME, "value", "Bye"));
        long id = last().id();

        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", (int) id))
                        .isSuccess())
                .isTrue();

        assertThat(welcome()).isEqualTo("Hello");
        ChangeRecord rollback = last();
        assertThat(rollback.source()).isEqualTo(ChangeRecord.Source.ROLLBACK);
        assertThat(rollback.undoes()).isEqualTo(id);
        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", (int) id))
                        .error()
                        .code())
                .isEqualTo("History.AlreadyRolledBack");
        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", 999))
                        .error()
                        .code())
                .isEqualTo("History.Unknown");
    }

    @Test
    void playerPreferencesAreNotRecorded() {
        int before = platform.changeLog.records.size();

        platform.commands.run("cc-pref", admin, Map.of("id", "controlcore:show-welcome-message", "value", "false"));

        assertThat(platform.changeLog.records).hasSize(before);
    }

    @Test
    void modChangesAreRecordedAsTheMod() {
        ControlCoreApi.get().settings().write(SettingId.of(WELCOME), Optional.empty(), "From a mod");

        ChangeRecord record = last();
        assertThat(record.source()).isEqualTo(ChangeRecord.Source.API);
        assertThat(record.actor().isMod()).isTrue();
    }

    @Test
    void removedCustomBindsComeBackWithRollback() {
        platform.commands.run(
                "cc-keybind-add", admin, Map.of("name", "warp-spawn", "keys", "ctrl+r", "command", "warp spawn"));
        platform.commands.run("cc-keybind-remove", admin, Map.of("name", "warp-spawn"));
        ChangeRecord removal = last();
        assertThat(removal.kind()).isEqualTo(ChangeRecord.Kind.CUSTOM_BIND);
        assertThat(removal.newValue()).isEmpty();

        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", (int) removal.id()))
                        .isSuccess())
                .isTrue();

        assertThat(platform.customKeyBinds.all()).anySatisfy(bind -> {
            assertThat(bind.name()).isEqualTo("warp-spawn");
            assertThat(bind.command()).isEqualTo("warp spawn");
        });
    }

    @Test
    void permissionRulesAreRecordedAndRolledBack() {
        platform.commands.run("cc-perm", admin, Map.of("target", WELCOME, "side", "edit", "node", "staff.motd"));
        ChangeRecord rule = last();
        assertThat(rule.kind()).isEqualTo(ChangeRecord.Kind.PERMISSION);
        assertThat(rule.target()).isEqualTo("setting:" + WELCOME);

        platform.commands.run("cc-rollback", admin, Map.of("change", (int) rule.id()));

        assertThat(platform.settingPermissions.all().getOrDefault("setting:" + WELCOME, null))
                .satisfiesAnyOf(
                        found -> assertThat(found).isNull(),
                        found -> assertThat(found.isInherit()).isTrue());
    }

    @Test
    void onlyAdminsAndHistoryNodesMaySeeIt() {
        platform.deniedNodes.add("settings");
        platform.deniedNodes.add("admin");
        platform.deniedNodes.add("history");
        platform.deniedNodes.add("history.rollback");

        assertThat(platform.commands.run("cc-history", admin, Map.of()).error().code())
                .isEqualTo("History.Forbidden");
        assertThat(platform.commands
                        .run("cc-rollback", admin, Map.of("change", 1))
                        .error()
                        .code())
                .isIn("History.Forbidden", "History.Unknown");
    }

    @Test
    void oldChangesAreDroppedAfterTheConfiguredDays() {
        platform.commands.run("cc-set", admin, Map.of("id", WELCOME, "value", "Old"));
        platform.clock.now = platform.clock.now.plus(Duration.ofDays(400));

        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:history-days", "value", "30"));

        assertThat(platform.changeLog.records)
                .noneSatisfy(record -> assertThat(record.newValue()).isEqualTo("Old"));
        assertThat(reply("cc-history", Map.of("filter", "Old"))).doesNotContain("→ Old");
    }

    private ChangeRecord last() {
        return platform.changeLog.records.getLast();
    }

    private String welcome() {
        return ControlCoreApi.get()
                .settings()
                .read(SettingId.of(WELCOME), Optional.empty())
                .value()
                .value();
    }

    private String reply(String command, Map<String, Object> args) {
        return platform.commands.run(command, admin, args).value().text().orElse("");
    }
}
