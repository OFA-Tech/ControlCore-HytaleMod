package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.fakes.RecordingCommandRunner;
import org.junit.jupiter.api.Test;

/** Key actions and admins' key binds, wired as on a server. */
class KeysRuntimeTest extends RuntimeTestBase {

    @Test
    void playersRunAndRebindKeyActionsOfOtherMods() {
        runtime.setup();
        runtime.start();
        PlayerInfo player = platform.players.join("Player");
        ControlCoreApi.get()
                .keymaps()
                .register(KeyAction.of("homes", "home")
                        .displayName("Go home")
                        .command("home")
                        .defaultKeys(Key.R));

        platform.input.press(player.id(), KeySignal.ABILITY_3);
        var bind = platform.commands.run("cc-bind", player, Map.of("id", "homes:home", "keys", "ctrl+f"));
        platform.clock.now = platform.clock.now.plusSeconds(1);
        platform.input.hold(player.id(), KeySignal.CROUCHING);
        platform.input.press(player.id(), KeySignal.USE);
        var list = platform.commands.run("cc-keys", player, Map.of());

        assertThat(bind.isSuccess()).isTrue();
        assertThat(platform.commandRunner.runs)
                .extracting(run -> run.command())
                .containsExactly("home", "prefs", "home");
        assertThat(list.value().text().orElseThrow())
                .contains("Go home: CTRL + F (yours)", "Open my preferences: CTRL + F");
        assertThat(platform.commands.commands.get("cc-bind-server").spec().permission())
                .isPresent();
    }

    @Test
    void adminsCreateServerKeyBindsThatOnlyPermittedPlayersCanUse() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");

        var add = platform.commands.run(
                "cc-keybind-add-server",
                admin,
                Map.of("name", "announce", "keys", "ctrl+r", "command", "/say {player} pressed it"));
        platform.input.hold(admin.id(), KeySignal.CROUCHING);
        platform.input.press(admin.id(), KeySignal.ABILITY_3);
        platform.deniedNodes.add("keys.server");
        platform.clock.now = platform.clock.now.plusSeconds(1);
        platform.input.press(admin.id(), KeySignal.ABILITY_3);
        var remove = platform.commands.run("cc-keybind-remove", admin, Map.of("name", "announce"));

        assertThat(add.value().text().orElseThrow()).contains("custom:announce", "as the server");
        assertThat(platform.commandRunner.runs)
                .containsExactly(new RecordingCommandRunner.Run(null, "say Admin pressed it"));
        assertThat(remove.isSuccess()).isTrue();
        assertThat(platform.customKeyBinds.binds).isEmpty();
        assertThat(platform.commands.commands.get("cc-keybind-add").spec().permission())
                .isPresent();
    }
}
