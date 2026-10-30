package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
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

class KeyDetectorBanca2Test {

    private final FakePlayers players = new FakePlayers();
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
            new KeyDispatcher(keymaps, new RecordingCommandRunner(), players, new FakeClock(), new RecordingLog());
    private final KeyDetector detector = new KeyDetector(dispatcher, scheduler);
    private final List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
    private PlayerId caio;

    @BeforeEach
    void setUp() {
        caio = players.join("Caio").id();
        input.listen(detector);
    }

    @Test
    void ctrlAndShiftHeldTogetherAreBothKept() {
        detector.fromGame(caio, results::add);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING);
        input.press(caio, KeySignal.ABILITY_3);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+SHIFT+R")));
    }

    @Test
    void runningAtStartThenAddingCtrlKeepsOnlyCtrl() {
        input.hold(caio, KeySignal.SPRINTING);
        detector.fromGame(caio, results::add);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING);
        input.press(caio, KeySignal.USE);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+F")));
    }

    @Test
    void jumpingAtStartIsNotAHold() {
        input.hold(caio, KeySignal.JUMPING);
        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.ABILITY_1);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("Q")));
    }

    @Test
    void shiftHeldAtStartReleasedAndPressedAgainCountsAsAHold() {
        input.hold(caio, KeySignal.SPRINTING);
        detector.fromGame(caio, results::add);
        input.hold(caio);
        input.hold(caio, KeySignal.SPRINTING);
        input.press(caio, KeySignal.ABILITY_3);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("SHIFT+R")));
    }

    @Test
    void everyDetectedPressWithAllHoldsParsesAndBecomesATrigger() {
        for (KeySignal signal : KeySignal.values()) {
            if (!signal.isPress() || signal == KeySignal.WALK_TAP) {
                continue;
            }
            results.clear();
            detector.fromGame(caio, results::add);
            input.hold(caio, KeySignal.CROUCHING, KeySignal.WALKING, KeySignal.SPRINTING);
            input.press(caio, signal);
            input.hold(caio);

            assertThat(results).as(signal.name()).hasSize(1);
            String keys = results.getFirst().orElseThrow().keys();
            assertThat(KeyCombo.parse(keys).isSuccess()).as(keys).isTrue();
            assertThat(KeyCombo.parse(keys).value().trigger().isSuccess())
                    .as(keys)
                    .isTrue();
        }
    }

    @Test
    void aGameDetectReplacesAPendingScreenOneAndTheScreenOfferIsIgnored() {
        List<Optional<KeyDetector.Detected>> screen = new ArrayList<>();
        long token = detector.fromScreen(caio, screen::add);
        detector.fromGame(caio, results::add);

        detector.offer(caio, token, Optional.of(new KeyDetector.Detected("NUMPAD_5")));
        input.press(caio, KeySignal.USE);

        assertThat(screen).isEmpty();
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")));
    }

    @Test
    void screenOfferWithNothingEndsTheDetection() {
        long token = detector.fromScreen(caio, results::add);
        detector.offer(caio, token, Optional.empty());

        assertThat(results).containsExactly(Optional.empty());
        assertThat(results).hasSize(1);
    }
}
