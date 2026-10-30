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

class KeyDetectorCancelBanca5Test {

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
    private PlayerId bia;

    @BeforeEach
    void setUp() {
        caio = players.join("Caio").id();
        bia = players.join("Bia").id();
        input.listen(detector);
        keymaps.register(KeyAction.of("homes", "home").command("home").defaultKeys(Key.R));
    }

    @Test
    void nothingPendingIsFalse() {
        assertThat(detector.cancel(caio)).isFalse();
    }

    @Test
    void aGameDetectIsCancelledButReportsFalseSoNoScreenIsClosed() {
        detector.fromGame(caio, results::add);

        assertThat(detector.cancel(caio)).isFalse();
        assertThat(scheduler.entries).allMatch(entry -> entry.cancelled);
        input.press(caio, KeySignal.ABILITY_3);
        scheduler.runAll();
        assertThat(results).isEmpty();
    }

    @Test
    void aScreenDetectReportsTrueOnceAndThenFalse() {
        long token = detector.fromScreen(caio, results::add);

        assertThat(detector.cancel(caio)).isTrue();
        assertThat(detector.cancel(caio)).isFalse();
        assertThat(scheduler.entries).allMatch(entry -> entry.cancelled);
        detector.offer(caio, token, Optional.of(new KeyDetector.Detected("F")));
        assertThat(results).isEmpty();
    }

    @Test
    void aScreenDetectThatAlreadyFinishedOrTimedOutReportsFalse() {
        long token = detector.fromScreen(caio, results::add);
        detector.offer(caio, token, Optional.of(new KeyDetector.Detected("F")));
        assertThat(detector.cancel(caio)).isFalse();

        detector.fromScreen(caio, results::add);
        scheduler.runAll();
        assertThat(detector.cancel(caio)).isFalse();
        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("F")), Optional.empty());
    }

    @Test
    void aScreenDetectReplacedByAGameDetectReportsFalse() {
        detector.fromScreen(caio, results::add);
        detector.fromGame(caio, results::add);

        assertThat(detector.cancel(caio)).isFalse();
    }

    @Test
    void cancellingOnePlayerLeavesTheOtherPending() {
        detector.fromScreen(caio, results::add);
        long biaToken = detector.fromScreen(bia, results::add);

        assertThat(detector.cancel(caio)).isTrue();
        detector.offer(bia, biaToken, Optional.of(new KeyDetector.Detected("G")));

        assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("G")));
    }
}
