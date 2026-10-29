package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.fakes.FakeClock;
import net.ofatech.controlcore.fakes.FakeCustomKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyBindStore;
import net.ofatech.controlcore.fakes.FakeKeyGameActionStore;
import net.ofatech.controlcore.fakes.FakePlayerData;
import net.ofatech.controlcore.fakes.FakePlayerInput;
import net.ofatech.controlcore.fakes.FakePlayers;
import net.ofatech.controlcore.fakes.FakeScheduler;
import net.ofatech.controlcore.fakes.RecordingCommandRunner;
import net.ofatech.controlcore.fakes.RecordingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectorTest {

    private final FakePlayers players = new FakePlayers();
    private final RecordingCommandRunner runner = new RecordingCommandRunner();
    private final FakePlayerInput input = new FakePlayerInput();
    private final FakeScheduler scheduler = new FakeScheduler();
    private final Set<String> denied = new HashSet<>();
    private final KeymapRegistry keymaps = new KeymapRegistry(
            new FakeKeyBindStore(),
            new FakeCustomKeyBindStore(),
            new FakeKeyGameActionStore(),
            new FakePlayerData(),
            (player, node) -> !denied.contains(node),
            new RecordingLog(),
            ChangeRecorder.NONE);
    private final KeyDispatcher dispatcher =
            new KeyDispatcher(keymaps, runner, players, new FakeClock(), new RecordingLog());
    private final KeyDetector detector = new KeyDetector(dispatcher, scheduler);
    private final List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
    private PlayerId caio;

    @BeforeEach
    void setUp() {
        caio = players.join("Caio").id();
        input.listen(detector);
        keymaps.register(KeyAction.of("homes", "home").command("home {player}").defaultKeys(Key.R));
    }

    @Test
    void detectsTheNextPressWithItsHold() {
        detector.fromGame(caio, results::add);
        input.hold(caio, KeySignal.CROUCHING, KeySignal.MOVING);

        boolean cancelled = input.press(caio, KeySignal.ABILITY_3);

        assertThat(cancelled).isTrue();
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+R")));
        assertThat(runner.runs).isEmpty();
        assertThat(scheduler.entries).allMatch(entry -> entry.cancelled);
    }

    @Test
    void timesOutAfterTenSeconds() {
        detector.fromGame(caio, results::add);

        assertThat(scheduler.entries.getFirst().delay).isEqualTo(Duration.ofSeconds(10));
        scheduler.runAll();

        assertThat(results).containsExactly(Optional.empty());
        input.press(caio, KeySignal.ABILITY_3);
        assertThat(results).hasSize(1);
        assertThat(runner.runs).hasSize(1);
    }

    @Test
    void aSecondDetectReplacesTheFirst() {
        List<Optional<KeyDetector.Detected>> first = new ArrayList<>();
        detector.fromGame(caio, first::add);
        detector.fromGame(caio, results::add);

        input.press(caio, KeySignal.USE);

        assertThat(first).isEmpty();
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")));
        assertThat(scheduler.entries.getFirst().cancelled).isTrue();
    }

    @Test
    void cancellingDropsTheDetection() {
        detector.fromGame(caio, results::add);
        detector.cancel(caio);

        input.press(caio, KeySignal.ABILITY_3);
        scheduler.runAll();

        assertThat(results).isEmpty();
        assertThat(runner.runs).hasSize(1);
    }

    @Test
    void hotbarIsDetectedAsADigitWithoutCancellingTheSwap() {
        detector.fromGame(caio, results::add);

        boolean cancelled = input.press(caio, KeySignal.HOTBAR_3);

        assertThat(cancelled).isFalse();
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("3")));
    }

    @Test
    void theScreenWaitsForItsOfferAndIgnoresGamePresses() {
        long screen1 = detector.fromScreen(caio, results::add);

        input.press(caio, KeySignal.ABILITY_3);
        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("CTRL+NUMPAD_5")));

        assertThat(runner.runs).hasSize(1);
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+NUMPAD_5")));
    }

    @Test
    void theScreenWaitsLongerThanTheGameButNotForever() {
        long screen1 = detector.fromScreen(caio, results::add);
        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("NUMPAD_5")));
        long screen2 = detector.fromScreen(caio, results::add);

        scheduler.runAll();
        detector.offer(caio, screen2, Optional.of(new KeyDetector.Detected("NUMPAD_6")));

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("NUMPAD_5")), Optional.empty());
        assertThat(scheduler.entries).extracting(entry -> entry.delay).contains(KeyDetector.SCREEN_TIMEOUT);
    }

    @Test
    void withoutDetectionNormalBindsStillRun() {
        input.press(caio, KeySignal.ABILITY_3);

        assertThat(runner.runs).hasSize(1);
        assertThat(results).isEmpty();
    }

    @Test
    void bindsOnKeysTheServerCantHearPointToDetectKey() {
        String problems = KeymapsText.problems(
                net.ofatech.controlcore.fakes.TestTexts.english(),
                net.ofatech.controlcore.domain.model.keys.KeyBinds.parse("numpad_5")
                        .value());

        assertThat(problems).contains("NUMPAD 5").contains("Detect key");
    }
}
