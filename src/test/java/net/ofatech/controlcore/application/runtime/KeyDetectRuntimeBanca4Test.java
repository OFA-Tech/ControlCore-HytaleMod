package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PanelRights;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.KeyDetection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyDetectRuntimeBanca4Test extends RuntimeTestBase {

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
