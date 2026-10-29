package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

class KeyDetectorBancaTest {

    private final FakePlayers players = new FakePlayers();
    private final RecordingCommandRunner runner = new RecordingCommandRunner();
    private final FakePlayerInput input = new FakePlayerInput();
    private final FakeScheduler scheduler = new FakeScheduler();
    private final KeymapRegistry keymaps = new KeymapRegistry(
            new FakeKeyBindStore(),
            new FakeCustomKeyBindStore(),
            new FakeKeyGameActionStore(),
            new FakePlayerData(),
            (player, node) -> true,
            new RecordingLog(),
            ChangeRecorder.NONE);
    private final KeyDispatcher dispatcher =
            new KeyDispatcher(keymaps, runner, players, new FakeClock(), new RecordingLog());
    private final KeyDetector detector = new KeyDetector(dispatcher, scheduler);
    private final List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
    private PlayerId caio;
    private PlayerId bia;

    @BeforeEach
    void setUp() {
        caio = players.join("Caio").id();
        bia = players.join("Bia").id();
        input.listen(detector);
        keymaps.register(KeyAction.of("homes", "home").command("home {player}").defaultKeys(Key.R));
    }

    @Test
    void forgetDropsTheDetectionAndItsTimeout() {
        detector.fromGame(caio, results::add);

        detector.forget(caio);
        input.press(caio, KeySignal.ABILITY_3);
        scheduler.runAll();

        assertThat(results).isEmpty();
        assertThat(runner.runs).hasSize(1);
        assertThat(scheduler.entries).allMatch(entry -> entry.cancelled);
    }

    @Test
    void forgetAlsoDropsHeldKeysSoTheyDoNotLeakIntoTheNextDetection() {
        input.hold(caio, KeySignal.CROUCHING);
        detector.forget(caio);

        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.ABILITY_3);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("R")));
    }

    @Test
    void forgetOnAScreenDetectionIgnoresTheLateOffer() {
        long screen1 = detector.fromScreen(caio, results::add);

        detector.forget(caio);
        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("NUMPAD_5")));

        assertThat(results).isEmpty();
    }

    @Test
    void offerAfterCancelIsIgnored() {
        long screen1 = detector.fromScreen(caio, results::add);
        detector.cancel(caio);

        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("NUMPAD_5")));

        assertThat(results).isEmpty();
    }

    @Test
    void offerWhileDetectingFromTheGameIsIgnored() {
        long game = detector.fromGame(caio, results::add);

        detector.offer(caio, game, Optional.of(new KeyDetector.Detected("NUMPAD_5")));

        assertThat(results).isEmpty();
        input.press(caio, KeySignal.USE);
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")));
    }

    @Test
    void offerAfterTheScreenAlreadyFinishedIsIgnored() {
        long screen1 = detector.fromScreen(caio, results::add);
        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("Q")));

        detector.offer(caio, screen1, Optional.of(new KeyDetector.Detected("E")));

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("Q")));
    }

    @Test
    void playersAreIndependent() {
        List<Optional<KeyDetector.Detected>> biaResults = new ArrayList<>();
        detector.fromGame(caio, results::add);
        detector.fromGame(bia, biaResults::add);

        input.press(bia, KeySignal.USE);
        detector.forget(bia);

        assertThat(biaResults).containsExactly(Optional.of(new KeyDetector.Detected("F")));
        assertThat(results).isEmpty();
        input.press(caio, KeySignal.ABILITY_1);
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("Q")));
    }

    @Test
    void timeoutOfAReplacedDetectionDoesNotEndTheNewOne() {
        List<Optional<KeyDetector.Detected>> first = new ArrayList<>();
        detector.fromGame(caio, first::add);
        FakeScheduler.Entry oldTimeout = scheduler.entries.getFirst();
        detector.fromGame(caio, results::add);

        oldTimeout.work.run();

        assertThat(results).isEmpty();
        assertThat(first).isEmpty();
        input.press(caio, KeySignal.USE);
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")));
    }

    @Test
    void lateTimeoutAfterAPressDoesNothing() {
        detector.fromGame(caio, results::add);
        FakeScheduler.Entry timeout = scheduler.entries.getFirst();
        input.press(caio, KeySignal.USE);

        timeout.work.run();

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")));
    }

    @Test
    void everyPressTheServerHearsIsDetected() {
        for (KeySignal signal : KeySignal.values()) {
            if (!signal.isPress()) {
                continue;
            }
            List<Optional<KeyDetector.Detected>> one = new ArrayList<>();
            detector.fromGame(caio, one::add);
            input.press(caio, signal);
            assertThat(one).as(signal.name()).hasSize(1);
            assertThat(net.ofatech.controlcore.domain.model.keys.KeyBinds.parse(
                                    one.getFirst().orElseThrow().keys())
                            .isSuccess())
                    .as(signal.name())
                    .isTrue();
        }
    }

    @Test
    void walkTapWithAltHeldIsJustAlt() {
        input.hold(caio, KeySignal.WALKING);
        detector.fromGame(caio, results::add);

        input.press(caio, KeySignal.WALK_TAP);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("ALT")));
    }

    @Test
    void twoHoldsAreBothKeptInCtrlAltShiftSpaceOrder() {
        detector.fromGame(caio, results::add);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING);

        input.press(caio, KeySignal.ABILITY_3);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+SHIFT+R")));
    }

    @Test
    void runningOrJumpingWhenDetectStartsIsNotAHold() {
        input.hold(caio, KeySignal.SPRINTING, KeySignal.JUMPING);
        detector.fromGame(caio, results::add);

        input.press(caio, KeySignal.ABILITY_2);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("E")));
    }

    @Test
    void ability4AndDodgeAreDetectedByTheirActionNames() {
        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.ABILITY_4);
        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.DODGE);
        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.HOTBAR_7);

        assertThat(results)
                .containsExactly(
                        Optional.of(new KeyDetector.Detected("ABILITY_4")),
                        Optional.of(new KeyDetector.Detected("DODGE")),
                        Optional.of(new KeyDetector.Detected("7")));
    }

    @Test
    void aStaleCaptureScreenDoesNotFinishTheNewerDetection() {
        List<Optional<KeyDetector.Detected>> first = new ArrayList<>();
        long screen1 = detector.fromScreen(caio, first::add);
        long screen2 = detector.fromScreen(caio, results::add);

        detector.offer(caio, screen1, Optional.empty());
        detector.offer(caio, screen2, Optional.of(new KeyDetector.Detected("NUMPAD_5")));

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("NUMPAD_5")));
    }
}
