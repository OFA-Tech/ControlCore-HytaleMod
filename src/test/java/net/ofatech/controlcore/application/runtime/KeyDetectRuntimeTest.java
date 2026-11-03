package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.keymaps.KeyDetector;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyDetectRuntimeTest {

    @Nested
    class Detection extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void defaultsAreFromTheGameAndShortLists() {
            assertThat(get("controlcore:key-detect-mode")).contains("From the game");
            assertThat(get("controlcore:all-keys-in-lists")).contains("Value: false (default)");

            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));

            assertThat(get("controlcore:key-detect-mode")).contains("Capture screen");
        }

        @Test
        void detectFromTheGameTakesTheNextPressAndShowsAToast() {
            List<Optional<String>> results = new ArrayList<>();
            KeyDetection detection = detection();
            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.GAME);

            detection.detect(detection.mode(), results::add);
            platform.input.hold(admin.id(), KeySignal.CROUCHING);
            boolean cancelled = platform.input.press(admin.id(), KeySignal.ABILITY_3);

            assertThat(cancelled).isTrue();
            assertThat(results).containsExactly(Optional.of("CTRL+R"));
            assertThat(platform.overlay.everToasted).anyMatch(text -> text.contains("10 s"));
            assertThat(platform.overlay.shown.get(admin.id()).toasts()).isEmpty();
        }

        @Test
        void detectTimesOutWithNothing() {
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.scheduler.runAll();

            assertThat(results).contains(Optional.empty());
        }

        @Test
        void screenModeUsesTheCaptureScreen() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            List<Optional<String>> results = new ArrayList<>();
            KeyDetection detection = detection();
            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);

            detection.detect(detection.mode(), results::add);
            platform.input.press(admin.id(), KeySignal.ABILITY_3);
            platform.keyCapture.press(admin.id(), "CTRL+NUMPAD_5");

            assertThat(platform.keyCapture.opened).containsExactly(admin.id());
            assertThat(results).containsExactly(Optional.of("CTRL+NUMPAD_5"));
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }

        private String get(String id) {
            return String.valueOf(platform.commands.run("cc-get", admin, Map.of("id", id)));
        }
    }

    @Nested
    class PromptToastAndLeaving extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void thePromptToastGoesAwayAsSoonAsTheKeyIsDetected() {
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);
            assertThat(platform.overlay.shown.get(admin.id()).toasts()).hasSize(1);

            platform.input.press(admin.id(), KeySignal.ABILITY_3);

            assertThat(results).hasSize(1).first().matches(Optional::isPresent);
            assertThat(platform.overlay.shown.get(admin.id()).toasts()).isEmpty();
        }

        @Test
        void thePromptToastGoesAwayWhenThePanelIsOpenedAgain() {
            detection().detect(detection().mode(), result -> {});

            detection();

            assertThat(platform.overlay.shown.get(admin.id()).toasts()).isEmpty();
        }

        @Test
        void leavingForgetsTheHeldKeysOfThePlayer() {
            platform.connectedPublisher.publish(new PlayerLeft(admin));

            assertThat(platform.input.forgotten).containsExactly(admin.id());
        }

        @Test
        void leavingWhileDetectingFromTheGameDropsTheDetection() {
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.connectedPublisher.publish(new PlayerLeft(admin));
            platform.scheduler.runAll();
            boolean cancelled = platform.input.press(admin.id(), KeySignal.ABILITY_3);

            assertThat(results).isEmpty();
            assertThat(cancelled).isFalse();
        }

        @Test
        void leavingWhileOnTheCaptureScreenIgnoresTheLateKey() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.connectedPublisher.publish(new PlayerLeft(admin));
            platform.keyCapture.press(admin.id(), "NUMPAD_5");

            assertThat(results).isEmpty();
        }

        @Test
        void escapeOnTheCaptureScreenEndsWithNothing() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.keyCapture.press(admin.id(), null);

            assertThat(results).containsExactly(Optional.empty());
        }

        @Test
        void aNewDetectAfterTheOldOneEndedWorks() {
            List<Optional<String>> first = new ArrayList<>();
            List<Optional<String>> second = new ArrayList<>();
            KeyDetection detection = detection();
            detection.detect(detection.mode(), first::add);
            platform.scheduler.runAll();

            detection.detect(detection.mode(), second::add);
            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(first).containsExactly(Optional.empty());
            assertThat(second).containsExactly(Optional.of("F"));
        }

        @Test
        void settingsChangeIsSeenByAnAlreadyBuiltPanel() {
            KeyDetection detection = detection();
            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.GAME);
            assertThat(detection.allKeys()).isFalse();

            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));

            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
            assertThat(detection.allKeys()).isTrue();
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }
    }

    @Nested
    class PromptToasts extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void thePromptGoesAwayWhenTheDetectionExpires() {
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(KeyDetection.Mode.GAME, results::add);
            assertThat(overlay().toasts()).hasSize(1);

            platform.scheduler.runAll();

            assertThat(results).containsExactly(Optional.empty());
            assertThat(overlay().toasts()).isEmpty();
        }

        @Test
        void detectingTwiceInARowShowsOnlyOnePrompt() {
            KeyDetection detection = detection();
            detection.detect(KeyDetection.Mode.GAME, result -> {});
            detection.detect(KeyDetection.Mode.GAME, result -> {});

            assertThat(overlay().toasts()).hasSize(1);
        }

        @Test
        void thePromptGoesAwayButOtherToastsStay() {
            NotificationService notifications = service();
            notifications.toast(admin.id(), "", "other", ToastLevel.INFO, 30);
            detection().detect(KeyDetection.Mode.GAME, result -> {});
            assertThat(overlay().toasts()).hasSize(2);

            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(overlay().toasts()).extracting(Overlay.Line::text).containsExactly("other");
        }

        @Test
        void reopeningThePanelAfterTheDetectionEndedIsHarmless() {
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(KeyDetection.Mode.GAME, results::add);
            platform.input.press(admin.id(), KeySignal.USE);

            detection();
            detection();

            assertThat(results).containsExactly(Optional.of("F"));
            assertThat(overlay().toasts()).isEmpty();
        }

        @Test
        void withHytaleToastsThePromptGoesThereAndDetectionStillWorks() {
            platform.commands.run(
                    "cc-pref", admin, Map.of("id", "controlcore:toast-position", "value", "Hytale notifications"));
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(KeyDetection.Mode.GAME, results::add);

            assertThat(platform.overlay.hytaleToasts).hasSize(1);
            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(results).containsExactly(Optional.of("F"));
        }

        @Test
        void showToastWithHytalePositionGivesANoOpDismiss() {
            platform.commands.run(
                    "cc-pref", admin, Map.of("id", "controlcore:toast-position", "value", "Hytale notifications"));

            Runnable dismiss = service().showToast(admin.id(), "T", "x", ToastLevel.INFO, 5);

            assertThat(dismiss).isNotNull();
            dismiss.run();
            dismiss.run();
            assertThat(platform.overlay.hytaleToasts).containsExactly("info:x");
        }

        @Test
        void showToastForNobodyOrHiddenToastsGivesANoOpDismiss() {
            NotificationService notifications = service();

            Runnable none = notifications.showToast(null, "T", "x", ToastLevel.INFO, 5);
            platform.commands.run("cc-pref", admin, Map.of("id", "controlcore:hud.toasts", "value", "false"));
            Runnable hidden = notifications.showToast(admin.id(), "T", "x", ToastLevel.INFO, 5);

            none.run();
            hidden.run();
            Overlay shown = platform.overlay.shown.get(admin.id());
            assertThat(shown == null || shown.toasts().isEmpty()).isTrue();
        }

        @Test
        void dismissRemovesOnlyItsOwnToastAndIsSafeAfterExpiry() {
            NotificationService notifications = service();
            Runnable first = notifications.showToast(admin.id(), "", "one", ToastLevel.INFO, 5);
            notifications.showToast(admin.id(), "", "two", ToastLevel.INFO, 5);

            first.run();
            assertThat(overlay().toasts()).extracting(Overlay.Line::text).containsExactly("two");

            platform.scheduler.runAll();
            first.run();
            assertThat(overlay().toasts()).isEmpty();
        }

        @Test
        void theCaptureScreenGetsTheAllKeysChoice() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            detection().detect(KeyDetection.Mode.SCREEN, result -> {});
            platform.keyCapture.press(admin.id(), null);

            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));
            detection().detect(KeyDetection.Mode.SCREEN, result -> {});

            assertThat(platform.keyCapture.anyKey).containsExactly(false, true);
        }

        private NotificationService service() {
            assertThat(ControlCoreApi.get().notifications()).isInstanceOf(NotificationService.class);
            return (NotificationService) ControlCoreApi.get().notifications();
        }

        private Overlay overlay() {
            return platform.overlay.shown.get(admin.id());
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }
    }

    @Nested
    class ScreenAndGameModes extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void aSecondScreenDetectReplacesTheFirstAndOnlyTheNewOneFinishes() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> first = new ArrayList<>();
            List<Optional<String>> second = new ArrayList<>();

            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
            detection.detect(detection.mode(), first::add);
            assertThat(detection.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
            detection.detect(detection.mode(), second::add);
            assertThat(platform.keyCapture.opened).hasSize(2);

            platform.keyCapture.press(admin.id(), "CTRL+SHIFT+NUMPAD_DIVIDE");

            assertThat(first).isEmpty();
            assertThat(second).containsExactly(Optional.of("CTRL+SHIFT+NUMPAD_DIVIDE"));
        }

        @Test
        void screenModeIgnoresGamePressesWhileCapturing() {
            screenMode();
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(results).isEmpty();
        }

        @Test
        void gameModeDetectsAbility4AndDodgeByName() {
            List<Optional<String>> results = new ArrayList<>();
            KeyDetection detection = detection();
            detection.detect(detection.mode(), results::add);
            platform.input.press(admin.id(), KeySignal.ABILITY_4);
            detection.detect(detection.mode(), results::add);
            platform.input.press(admin.id(), KeySignal.DODGE);

            assertThat(results).containsExactly(Optional.of("ABILITY_4"), Optional.of("DODGE"));
        }

        @Test
        void aDetectionStillAllowedWhileRightsAreUnchanged() {
            platform.commands.run(COMMAND, admin, Map.of());
            assertThat(platform.controlPanel.lastContent.rights().get())
                    .isEqualTo(platform.controlPanel.lastContent.rights().get());
        }

        private void screenMode() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }
    }

    @Nested
    class RightsChangesAndTimeouts extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void rightsChangeWhenAdminIsTakenAway() {
            Supplier<PanelRights> rights = rights();
            PanelRights atStart = rights.get();

            platform.deniedNodes.addAll(List.of("settings", "admin"));

            assertThat(rights.get()).isNotEqualTo(atStart);
        }

        @Test
        void rightsChangeWhenOnlyTheHistoryPermissionIsTakenAwayFromAModerator() {
            PlayerInfo moderator = platform.players.join("Mod");
            platform.connectedPublisher.publish(new PlayerJoined(moderator));
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
            platform.commands.run(COMMAND, moderator, Map.of());
            Supplier<PanelRights> rights = platform.controlPanel.lastContent.rights();
            PanelRights atStart = rights.get();

            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin", "history"));

            assertThat(rights.get()).isNotEqualTo(atStart);
        }

        @Test
        void rightsChangeWhenThePanelIsTurnedOff() {
            Supplier<PanelRights> rights = rights();

            PanelRights atStart = rights.get();

            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:control-panel-enabled", "value", "off"));

            assertThat(rights.get()).isNotEqualTo(atStart);
        }

        @Test
        void rightsComeBackUnchangedWhenThePermissionIsGivenBack() {
            Supplier<PanelRights> rights = rights();
            PanelRights atStart = rights.get();
            platform.deniedNodes.add("history");
            platform.deniedNodes.remove("history");

            assertThat(rights.get()).isEqualTo(atStart);
        }

        @Test
        void unrelatedSettingsDoNotCountAsARightsChange() {
            Supplier<PanelRights> rights = rights();

            PanelRights atStart = rights.get();

            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:all-keys-in-lists", "value", "true"));

            assertThat(rights.get()).isEqualTo(atStart);
        }

        @Test
        void timeoutSecondsMatchesTheGameTimeoutAndTheToast() {
            assertThat(detection().gameTimeoutSeconds()).isEqualTo(KeyDetector.GAME_TIMEOUT.toSeconds());
        }

        @Test
        void screenSafetyTimeoutEndsWithNothingAndALateKeyIsIgnored() {
            screenMode();
            List<Optional<String>> results = new ArrayList<>();
            detection().detect(detection().mode(), results::add);

            platform.scheduler.runAll();
            platform.keyCapture.press(admin.id(), "NUMPAD_5");

            assertThat(results).containsExactly(Optional.empty());
        }

        @Test
        void secondScreenDetectReplacesTheFirstEvenWhenTheFirstScreenReportsLate() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> first = new ArrayList<>();
            List<Optional<String>> second = new ArrayList<>();
            detection.detect(detection.mode(), first::add);
            detection.detect(detection.mode(), second::add);

            platform.scheduler.runAll();

            assertThat(first).isEmpty();
            assertThat(second).containsExactly(Optional.empty());
        }

        @Test
        void noneDetectionAnswersRightAwayWithNothing() {
            List<Optional<String>> results = new ArrayList<>();

            KeyDetection.NONE.detect(KeyDetection.NONE.mode(), results::add);

            assertThat(KeyDetection.NONE.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
            assertThat(results).containsExactly(Optional.empty());
        }

        private void screenMode() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        }

        private Supplier<PanelRights> rights() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.rights();
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }
    }

    @Nested
    class CaptureScreenAndReopening extends RuntimeTestBase {

        private PlayerInfo admin;

        @BeforeEach
        void startUp() {
            runtime.setup();
            runtime.start();
            admin = platform.players.join("Admin");
            platform.connectedPublisher.publish(new PlayerJoined(admin));
        }

        @Test
        void screenTimeoutClosesTheCaptureScreen() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.SCREEN, results::add);
            assertThat(platform.keyCapture.opened).containsExactly(admin.id());

            platform.scheduler.runAll();

            assertThat(results).containsExactly(Optional.empty());
            assertThat(platform.keyCapture.closed).containsExactly(admin.id());
        }

        @Test
        void aCapturedKeyDoesNotCloseTheCaptureScreenFromTheServer() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.SCREEN, results::add);

            platform.keyCapture.press(admin.id(), "CTRL+NUMPAD_5");
            platform.scheduler.runAll();

            assertThat(results).containsExactly(Optional.of("CTRL+NUMPAD_5"));
            assertThat(platform.keyCapture.closed).isEmpty();
        }

        @Test
        void escOnTheCaptureScreenAlsoAsksToCloseIt() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.SCREEN, results::add);

            platform.keyCapture.press(admin.id(), null);

            assertThat(results).containsExactly(Optional.empty());
            assertThat(platform.keyCapture.closed).containsExactly(admin.id());
        }

        @Test
        void gameTimeoutNeverTouchesTheCaptureScreen() {
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.GAME, results::add);

            platform.scheduler.runAll();

            assertThat(results).containsExactly(Optional.empty());
            assertThat(platform.keyCapture.opened).isEmpty();
            assertThat(platform.keyCapture.closed).isEmpty();
        }

        @Test
        void openingThePanelAgainCancelsAPendingGameDetectSilently() {
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.GAME, results::add);

            detection();
            boolean swallowed = platform.input.press(admin.id(), KeySignal.USE);
            platform.scheduler.runAll();

            assertThat(results).isEmpty();
            assertThat(swallowed).isFalse();
        }

        @Test
        void openingThePanelAgainCancelsAPendingScreenDetectAndALateKeyIsIgnored() {
            screenMode();
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.SCREEN, results::add);

            detection();
            platform.keyCapture.press(admin.id(), "F");
            platform.scheduler.runAll();

            assertThat(results).isEmpty();
            assertThat(platform.keyCapture.closed).containsExactly(admin.id());
        }

        @Test
        void anotherPlayerOpeningThePanelDoesNotCancelMyDetect() {
            PlayerInfo other = platform.players.join("Other");
            platform.connectedPublisher.publish(new PlayerJoined(other));
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.GAME, results::add);

            platform.commands.run(COMMAND, other, Map.of());
            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(results).containsExactly(Optional.of("F"));
        }

        @Test
        void aDetectStartedAfterReopeningStillWorks() {
            detection().detect(KeyDetection.Mode.GAME, result -> {});
            KeyDetection fresh = detection();
            List<Optional<String>> results = new ArrayList<>();
            fresh.detect(KeyDetection.Mode.GAME, results::add);

            platform.input.press(admin.id(), KeySignal.USE);

            assertThat(results).containsExactly(Optional.of("F"));
        }

        @Test
        void bareClicksAreIgnoredAndGoToTheGameButCtrlClickIsDetected() {
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.GAME, results::add);

            platform.input.press(admin.id(), KeySignal.PRIMARY);
            platform.input.press(admin.id(), KeySignal.SECONDARY);
            assertThat(results).isEmpty();

            platform.input.hold(admin.id(), KeySignal.CROUCHING);
            platform.input.press(admin.id(), KeySignal.PRIMARY);

            assertThat(results).containsExactly(Optional.of("CTRL+MOUSE_LEFT"));
        }

        @Test
        void ctrlHeldBeforeDetectDoesNotTurnABareClickIntoACombo() {
            platform.input.hold(admin.id(), KeySignal.CROUCHING);
            KeyDetection detection = detection();
            List<Optional<String>> results = new ArrayList<>();
            detection.detect(KeyDetection.Mode.GAME, results::add);

            platform.input.press(admin.id(), KeySignal.PRIMARY);

            assertThat(results).isEmpty();
        }

        @Test
        void rightsAreEqualWhenNothingChangesAndDifferWhenAdminIsRemoved() {
            Supplier<PanelRights> rights = rights();
            PanelRights first = rights.get();
            PanelRights second = rights.get();

            assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);

            platform.deniedNodes.addAll(List.of("settings", "admin"));

            assertThat(rights.get()).isNotEqualTo(first);
        }

        @Test
        void rightsDifferWhenTheRollbackPermissionAloneChanges() {
            PlayerInfo moderator = platform.players.join("Mod");
            platform.connectedPublisher.publish(new PlayerJoined(moderator));
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));
            platform.commands.run(COMMAND, moderator, Map.of());
            Supplier<PanelRights> rights = platform.controlPanel.lastContent.rights();
            PanelRights atStart = rights.get();
            assertThat(atStart.rollback()).isTrue();

            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin", "history.rollback"));
            PanelRights after = rights.get();
            platform.deniedNodesFor.put(moderator.id(), List.of("settings", "admin"));

            assertThat(after).isNotEqualTo(atStart);
            assertThat(after.rollback()).isFalse();
            assertThat(after.history()).isTrue();
            assertThat(rights.get()).isEqualTo(atStart);
        }

        @Test
        void panelRightsRecordComparesEveryField() {
            PanelRights all = new PanelRights(true, true, true, true, true, true, true, true, true, true);
            assertThat(new PanelRights(true, true, true, true, true, true, true, true, true, true))
                    .isEqualTo(all);
            assertThat(new PanelRights(true, true, true, true, false, true, true, true, true, true))
                    .isNotEqualTo(all);
            assertThat(new PanelRights(true, true, true, true, true, true, true, true, true, false))
                    .isNotEqualTo(all);
        }

        @Test
        void noneDetectionUsesScreenModeAndHasNoGameTimeout() {
            assertThat(KeyDetection.NONE.mode()).isEqualTo(KeyDetection.Mode.SCREEN);
            assertThat(KeyDetection.NONE.gameTimeoutSeconds()).isZero();
            List<Optional<String>> results = new ArrayList<>();
            KeyDetection.NONE.detect(KeyDetection.Mode.GAME, results::add);
            assertThat(results).containsExactly(Optional.empty());
        }

        private void screenMode() {
            platform.commands.run("cc-set", admin, Map.of("id", "controlcore:key-detect-mode", "value", "screen"));
        }

        private Supplier<PanelRights> rights() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.rights();
        }

        private KeyDetection detection() {
            platform.commands.run(COMMAND, admin, Map.of());
            return platform.controlPanel.lastContent.keyDetection();
        }
    }
}
