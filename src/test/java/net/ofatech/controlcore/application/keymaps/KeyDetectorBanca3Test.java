package net.ofatech.controlcore.application.keymaps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.ofatech.controlcore.application.history.ChangeRecorder;
import net.ofatech.controlcore.domain.model.PlayerId;
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

class KeyDetectorBanca3Test {

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
    private PlayerId caio;

    @BeforeEach
    void setUp() {
        caio = players.join("Caio").id();
        input.listen(detector);
    }

    @Test
    void screenSafetyTimeoutIsExactlyFiveMinutesAndGameIsTenSeconds() {
        detector.fromScreen(caio, result -> {});
        detector.fromGame(caio, result -> {});

        assertThat(scheduler.entries)
                .extracting(entry -> entry.delay)
                .containsExactly(Duration.ofMinutes(5), Duration.ofSeconds(10));
        assertThat(KeyDetector.SCREEN_TIMEOUT).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void screenTimeoutEndsWithNothingAndALateKeyIsIgnored() {
        List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
        long token = detector.fromScreen(caio, results::add);

        scheduler.runAll();
        detector.offer(caio, token, Optional.of(new KeyDetector.Detected("NUMPAD_5")));

        assertThat(results).containsExactly(Optional.empty());
    }

    @Test
    void aSecondScreenDetectCancelsTheFirstTimerAndOnlyTheSecondEnds() {
        List<Optional<KeyDetector.Detected>> first = new ArrayList<>();
        List<Optional<KeyDetector.Detected>> second = new ArrayList<>();
        long token1 = detector.fromScreen(caio, first::add);
        long token2 = detector.fromScreen(caio, second::add);

        assertThat(scheduler.entries.getFirst().cancelled).isTrue();
        assertThat(scheduler.entries.get(1).cancelled).isFalse();

        detector.offer(caio, token1, Optional.of(new KeyDetector.Detected("A")));
        detector.offer(caio, token2, Optional.of(new KeyDetector.Detected("B")));
        detector.offer(caio, token2, Optional.of(new KeyDetector.Detected("C")));

        assertThat(first).isEmpty();
        assertThat(second).containsExactly(Optional.of(new KeyDetector.Detected("B")));
        assertThat(scheduler.entries.get(1).cancelled).isTrue();
    }

    @Test
    void theFirstScreensOldTimerFiringDoesNotEndTheSecond() {
        List<Optional<KeyDetector.Detected>> second = new ArrayList<>();
        detector.fromScreen(caio, result -> {});
        long token2 = detector.fromScreen(caio, second::add);

        scheduler.entries.getFirst().work.run();
        detector.offer(caio, token2, Optional.of(new KeyDetector.Detected("B")));

        assertThat(second).containsExactly(Optional.of(new KeyDetector.Detected("B")));
    }

    @Test
    void ctrlHeldAtStartReleasedAndHeldAgainCountsTogetherWithShiftKeptAtStart() {
        List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
        input.hold(caio, KeySignal.CROUCHING, KeySignal.SPRINTING);
        detector.fromGame(caio, results::add);
        input.hold(caio, KeySignal.SPRINTING);
        input.hold(caio, KeySignal.SPRINTING, KeySignal.CROUCHING);
        input.press(caio, KeySignal.USE);

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+F")));
    }

    @Test
    void releaseBeforeANewDetectDoesNotLeakIntoTheNextOne() {
        List<Optional<KeyDetector.Detected>> results = new ArrayList<>();
        input.hold(caio, KeySignal.CROUCHING);
        detector.fromGame(caio, results::add);
        input.hold(caio);
        input.press(caio, KeySignal.USE);

        input.hold(caio, KeySignal.CROUCHING);
        detector.fromGame(caio, results::add);
        input.press(caio, KeySignal.USE);

        assertThat(results)
                .containsExactly(
                        Optional.of(new KeyDetector.Detected("F")), Optional.of(new KeyDetector.Detected("F")));
    }

    @Test
    void gameTimeoutAfterAReplacedGameDetectEndsOnlyOnce() {
        List<Optional<KeyDetector.Detected>> first = new ArrayList<>();
        List<Optional<KeyDetector.Detected>> second = new ArrayList<>();
        detector.fromGame(caio, first::add);
        detector.fromGame(caio, second::add);

        scheduler.runAll();
        scheduler.runAll();

        assertThat(first).isEmpty();
        assertThat(second).containsExactly(Optional.empty());
    }
}
