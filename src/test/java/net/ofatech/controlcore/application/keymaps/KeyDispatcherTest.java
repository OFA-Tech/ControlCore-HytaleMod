package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.GameAction;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.fakes.FakeClock;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayerInput;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.RecordingCommandRunner;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDispatcherTest {

    private final FakePlayers players = new FakePlayers();
    private final FakeClock clock = new FakeClock();
    private final FakeKeyBindStore serverBinds = new FakeKeyBindStore();
    private final RecordingCommandRunner runner = new RecordingCommandRunner();
    private final FakePlayerInput input = new FakePlayerInput();
    private final KeymapRegistry keymaps = new KeymapRegistry(serverBinds, new FakePlayerData(), new RecordingLog());
    private PlayerId caio;

    @BeforeEach
    void setUp() {
        PlayerInfo info = players.join("Caio");
        caio = info.id();
        input.listen(new KeyDispatcher(keymaps, runner, players, clock, new RecordingLog()));
        keymaps.register(KeyAction.of("homes", "home").command("home {player}").defaultKeys(Key.F));
        keymaps.register(KeyAction.of("homes", "spawn").command("/spawn").defaultKeys(Key.CTRL, Key.F));
    }

    @Test
    void aPressRunsTheCommandAsThePlayerAndCancelsTheGameAction() {
        boolean cancelled = input.press(caio, KeySignal.USE);

        assertThat(cancelled).isTrue();
        assertThat(commands()).containsExactly("home Caio");
    }

    @Test
    void theCombinationWithMoreHeldKeysWins() {
        input.hold(caio, KeySignal.CROUCHING);

        input.press(caio, KeySignal.USE);

        assertThat(commands()).containsExactly("spawn");
    }

    @Test
    void pressesWithoutABindingKeepTheirGameAction() {
        assertThat(input.press(caio, KeySignal.ABILITY_1)).isFalse();
        assertThat(commands()).isEmpty();
    }

    @Test
    void repeatsWithinTheCooldownRunOnce() {
        input.press(caio, KeySignal.USE);
        input.press(caio, KeySignal.USE);
        clock.now = clock.now.plus(Duration.ofSeconds(1));
        input.press(caio, KeySignal.USE);

        assertThat(commands()).containsExactly("home Caio", "home Caio");
    }

    @Test
    void playersRebindTheirOwnKeys() {
        keymaps.bind(SettingId.of("homes:home"), caio, "alt+3");

        input.press(caio, KeySignal.USE);
        input.hold(caio, KeySignal.WALKING);
        boolean cancelled = input.press(caio, KeySignal.HOTBAR_3);

        assertThat(commands()).containsExactly("home Caio");
        assertThat(cancelled).as("hotbar changes are never cancelled").isFalse();
    }

    @Test
    void combinationsOfHeldStatesFireWhenTheyAllGoDown() {
        List<PlayerId> pressed = new ArrayList<>();
        keymaps.register(
                KeyAction.of("moves", "slide").onPress(pressed::add).defaultKeys(GameAction.SPRINT, GameAction.CROUCH));

        input.hold(caio, KeySignal.SPRINTING);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING, KeySignal.MOVING);

        assertThat(pressed).containsExactly(caio);
    }

    @Test
    void undetectableBindingsAreKeptButNeverFire() {
        keymaps.bind(SettingId.of("homes:home"), caio, "h");

        assertThat(input.press(caio, KeySignal.USE)).isFalse();
        assertThat(keymaps.keysFor(SettingId.of("homes:home"), caio)).hasToString("H");
        assertThat(KeymapsText.problems(keymaps.keysFor(SettingId.of("homes:home"), caio)))
                .contains("not available");
    }

    @Test
    void serverBindingsApplyToPlayersWithoutTheirOwn() {
        keymaps.bindServer(SettingId.of("homes:home"), "mouse_middle");

        input.press(caio, KeySignal.USE);
        input.press(caio, KeySignal.PICK);

        assertThat(commands()).containsExactly("home Caio");
        assertThat(serverBinds.values).containsEntry(SettingId.of("homes:home"), "MOUSE_MIDDLE");
    }

    @Test
    void actionsNeedSomethingToRun() {
        assertThat(keymaps.register(KeyAction.of("homes", "nothing")).error().code())
                .isEqualTo("Keys.NothingToRun");
    }

    private List<String> commands() {
        return runner.runs.stream().map(RecordingCommandRunner.Run::command).toList();
    }
}
