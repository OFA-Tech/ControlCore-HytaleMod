package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.application.settings.ModNames;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.ErrorType;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.fakes.FakeCustomKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyGameActionStore;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.RecordingLog;
import net.ofatech.controlcore.fakes.TestTexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeymapsEditorTest {

    @Nested
    class Editing {

        private static final SettingId HOME = SettingId.of("homes:home");

        private final KeymapRegistry keymaps = new KeymapRegistry(
                new FakeKeyBindStore(),
                new FakeCustomKeyBindStore(),
                new FakeKeyGameActionStore(),
                new FakePlayerData(),
                (player, node) -> true,
                new RecordingLog(),
                ChangeRecorder.NONE);
        private final KeymapsEditor editor = new KeymapsEditor(keymaps, TestTexts.standalone(), new ModNames());
        private final PlayerId alice = PlayerId.of(UUID.randomUUID());
        private final PlayerId bob = PlayerId.of(UUID.randomUUID());

        @BeforeEach
        void setUp() {
            keymaps.register(KeyAction.of("homes", "home")
                    .displayName("Go home")
                    .command("home")
                    .defaultKeys(Key.F));
        }

        @Test
        void keysGoFromTheModToTheServerToThePlayer() {
            assertThat(value(Optional.of(alice))).isEqualTo("F");

            editor.write(HOME, Optional.empty(), "ctrl+f");
            editor.write(HOME, Optional.of(alice), "alt+3");

            assertThat(value(Optional.empty())).isEqualTo("CTRL + F");
            assertThat(value(Optional.of(alice))).as("her own keys win").isEqualTo("ALT + 3");
            assertThat(value(Optional.of(bob)))
                    .as("everyone else gets the server's")
                    .isEqualTo("CTRL + F");

            editor.reset(HOME, Optional.of(alice));
            assertThat(value(Optional.of(alice))).isEqualTo("CTRL + F");

            editor.reset(HOME, Optional.empty());
            assertThat(value(Optional.of(bob))).as("back to the mod's keys").isEqualTo("F");
        }

        @Test
        void serverRowsShowTheModKeysAsDefaultAndReportChanges() {
            List<SettingChanged> changes = new ArrayList<>();
            editor.onAnyChange(changes::add);

            editor.write(HOME, Optional.empty(), "r");
            SettingSnapshot row =
                    editor.snapshot(SettingScope.SERVER, Optional.empty()).getFirst();

            assertThat(row.value()).isEqualTo("R");
            assertThat(row.definition().formattedDefault()).isEqualTo("F");
            assertThat(row.overridden()).isTrue();
            assertThat(changes).singleElement().satisfies(change -> {
                assertThat(change.scope()).isEqualTo(SettingScope.SERVER);
                assertThat(change.newValue()).isEqualTo("R");
            });
        }

        private String value(Optional<PlayerId> player) {
            return editor.read(HOME, player).value().value();
        }

        @Test
        void aViewerWhoIsNoLongerAdminCannotChangeTheServersKeysBindsOrGameActions() {
            boolean[] admin = {true};
            KeymapsEditor panel = editor.viewedBy(alice, () -> admin[0]);
            assertThat(panel.write(HOME, Optional.empty(), "ctrl+f").isSuccess())
                    .isTrue();
            assertThat(panel.add("warp", "ctrl+r", "warp spawn", false).isSuccess())
                    .isTrue();

            admin[0] = false;

            String refused = "Keys.ServerOnlyAdmins";
            assertThat(panel.write(HOME, Optional.empty(), "alt+3").error().code())
                    .isEqualTo(refused);
            assertThat(panel.reset(HOME, Optional.empty()).error().code()).isEqualTo(refused);
            assertThat(panel.add("home2", "ctrl+e", "home", false).error().code())
                    .isEqualTo(refused);
            assertThat(panel.remove("warp").error().code()).isEqualTo(refused);
            assertThat(panel.setGameAction(HOME, GameActionMode.REPLACE).error().code())
                    .isEqualTo(refused);
            assertThat(panel.find("warp")).as("the bind is still there").isPresent();
            assertThat(panel.gameAction(HOME)).isNotEqualTo(GameActionMode.REPLACE);
            assertThat(value(Optional.empty())).isEqualTo("CTRL + F");
            assertThat(panel.write(HOME, Optional.of(alice), "alt+3").isSuccess())
                    .as("her own keys still work")
                    .isTrue();
        }
    }

    @Nested
    class ServerChangesNeedAdmin {

        private static final SettingId HOME = SettingId.of("homes:home");
        private static final SettingId WARP = SettingId.of("custom:warp");

        private final KeymapRegistry keymaps = new KeymapRegistry(
                new FakeKeyBindStore(),
                new FakeCustomKeyBindStore(),
                new FakeKeyGameActionStore(),
                new FakePlayerData(),
                (player, node) -> true,
                new RecordingLog(),
                ChangeRecorder.NONE);
        private final KeymapsEditor base = new KeymapsEditor(keymaps, TestTexts.standalone(), new ModNames());
        private final PlayerId alice = PlayerId.of(UUID.randomUUID());

        @BeforeEach
        void setUp() {
            keymaps.register(KeyAction.of("homes", "home")
                    .displayName("Go home")
                    .command("home")
                    .defaultKeys(Key.F));
            base.write(HOME, Optional.empty(), "ctrl+f");
            base.add("warp", "ctrl+r", "warp spawn", false);
            base.setGameAction(HOME, GameActionMode.KEEP);
        }

        @Test
        void aViewerWhoWasNeverAdminIsRefusedOnEveryServerChangeWithTheSameError() {
            KeymapsEditor panel = base.viewedBy(alice, () -> false);

            assertRefused(panel.write(HOME, Optional.empty(), "alt+3"));
            assertRefused(panel.reset(HOME, Optional.empty()));
            assertRefused(panel.add("other", "ctrl+o", "say hi", true));
            assertRefused(panel.add("warp", "alt+r", "warp home", false));
            assertRefused(panel.remove("warp"));
            assertRefused(panel.setGameAction(HOME, GameActionMode.REPLACE));
            assertRefused(panel.setGameAction(HOME, GameActionMode.SERVER_SETTING));

            assertThat(base.read(HOME, Optional.empty()).value().value()).isEqualTo("CTRL + F");
            assertThat(keymaps.hasServerKeys(HOME)).isTrue();
            assertThat(keymaps.findCustom("warp")).hasValueSatisfying(bind -> {
                assertThat(bind.command()).isEqualTo("warp spawn");
            });
            assertThat(keymaps.findCustom("other")).isEmpty();
            assertThat(keymaps.gameAction(HOME)).isEqualTo(GameActionMode.KEEP);
        }

        @Test
        void theRefusalComesBeforeValidationSoBadInputStillSaysAdminsOnly() {
            KeymapsEditor panel = base.viewedBy(alice, () -> false);

            assertRefused(panel.write(SettingId.of("nope:nothing"), Optional.empty(), "ctrl+f"));
            assertRefused(panel.remove("does-not-exist"));
            assertRefused(panel.setGameAction(SettingId.of("nope:nothing"), GameActionMode.KEEP));
        }

        @Test
        void aNonAdminStillChangesAndResetsTheirOwnKeysAndCanReadTheServers() {
            KeymapsEditor panel = base.viewedBy(alice, () -> false);

            assertThat(panel.write(HOME, Optional.of(alice), "alt+3").isSuccess())
                    .isTrue();
            assertThat(panel.read(HOME, Optional.of(alice)).value().value()).isEqualTo("ALT + 3");
            assertThat(panel.reset(HOME, Optional.of(alice)).isSuccess()).isTrue();
            assertThat(panel.read(HOME, Optional.of(alice)).value().value()).isEqualTo("CTRL + F");
            assertThat(panel.read(HOME, Optional.empty()).isSuccess()).isTrue();
            assertThat(panel.snapshot(SettingScope.SERVER, Optional.empty())).isNotEmpty();
            assertThat(panel.find("warp")).isPresent();
            assertThat(panel.gameAction(HOME)).isEqualTo(GameActionMode.KEEP);
        }

        @Test
        void ownKeyChangesNeverAskWhetherTheViewerIsAdmin() {
            AtomicInteger asked = new AtomicInteger();
            KeymapsEditor panel = base.viewedBy(alice, () -> {
                asked.incrementAndGet();
                return false;
            });

            panel.write(HOME, Optional.of(alice), "alt+3");
            panel.reset(HOME, Optional.of(alice));
            panel.read(HOME, Optional.empty());

            assertThat(asked).hasValue(0);
        }

        @Test
        void adminIsCheckedOnEveryChangeSoRegainingAdminWorksAgain() {
            boolean[] admin = {false};
            KeymapsEditor panel = base.viewedBy(alice, () -> admin[0]);
            assertRefused(panel.write(HOME, Optional.empty(), "alt+3"));

            admin[0] = true;

            assertThat(panel.write(HOME, Optional.empty(), "alt+3").isSuccess()).isTrue();
            assertThat(panel.remove("warp").isSuccess()).isTrue();
            assertThat(panel.setGameAction(HOME, GameActionMode.REPLACE).isSuccess())
                    .isTrue();
            assertThat(panel.reset(HOME, Optional.empty()).isSuccess()).isTrue();
            assertThat(panel.add("again", "ctrl+o", "say hi", false).isSuccess())
                    .isTrue();
        }

        @Test
        void theBaseEditorWithoutAViewerIsStillUnrestricted() {
            assertThat(base.write(HOME, Optional.empty(), "alt+3").isSuccess()).isTrue();
            assertThat(base.setGameAction(HOME, GameActionMode.REPLACE).isSuccess())
                    .isTrue();
            assertThat(base.add("other", "ctrl+o", "say hi", true).isSuccess()).isTrue();
            assertThat(base.remove("other").isSuccess()).isTrue();
            assertThat(base.reset(HOME, Optional.empty()).isSuccess()).isTrue();
            assertThat(keymaps.hasServerKeys(HOME)).isFalse();
            assertThat(keymaps.gameAction(HOME)).isEqualTo(GameActionMode.REPLACE);
        }

        @Test
        void aViewedEditorDoesNotChangeTheBaseEditorsRights() {
            base.viewedBy(alice, () -> false);

            assertThat(base.write(HOME, Optional.empty(), "alt+3").isSuccess()).isTrue();
        }

        private static void assertRefused(Result<?> result) {
            assertThat(result.isFailure()).isTrue();
            Error error = result.error();
            assertThat(error.code()).isEqualTo("Keys.ServerOnlyAdmins");
            assertThat(error.type()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(error.message().key()).contains("controlcore.errors.keys.server_only_admins");
        }
    }
}
