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
import net.ofatech.controlcore.domain.model.keys.KeyCombo;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyDetectorTest {

    @Nested
    class Detection {

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
            keymaps.register(
                    KeyAction.of("homes", "home").command("home {player}").defaultKeys(Key.R));
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

        @Test
        void aBareClickIsNotDetectedButAClickWithAHoldIs() {
            detector.fromGame(caio, results::add);
            input.press(caio, KeySignal.PRIMARY);
            assertThat(results).isEmpty();

            input.hold(caio, KeySignal.CROUCHING);
            input.press(caio, KeySignal.PRIMARY);

            assertThat(results).containsExactly(Optional.of(new KeyDetector.Detected("CTRL+MOUSE_LEFT")));
        }
    }

    @Nested
    class ForgettingLateOffersAndHolds {

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
            keymaps.register(
                    KeyAction.of("homes", "home").command("home {player}").defaultKeys(Key.R));
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
        void everyPressTheServerHearsIsDetectedExceptBareClicks() {
            for (KeySignal signal : KeySignal.values()) {
                if (!signal.isPress() || signal == KeySignal.PRIMARY || signal == KeySignal.SECONDARY) {
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

    @Nested
    class HoldsAtStartAndReplacedDetects {

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

    @Nested
    class TimeoutsAndReplacedTimers {

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

    @Nested
    class Cancelling {

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

    @Nested
    class SettingsDefaults {

        @Test
        void beforeRegisterTheDefaultsAreUsedWithoutTouchingSettings() {
            KeyDetectSettings settings = new KeyDetectSettings(null);

            assertThat(settings.mode()).isEqualTo(KeyDetection.Mode.GAME);
            assertThat(settings.allKeys()).isFalse();
        }
    }
}
