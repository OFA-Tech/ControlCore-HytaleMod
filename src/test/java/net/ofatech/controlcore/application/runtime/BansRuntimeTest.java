package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.ports.game.Bans.Ban;
import org.junit.jupiter.api.Test;

class BansRuntimeTest extends RuntimeTestBase {

    @Test
    void playersAreBannedForAWhileAndUnbannedAndBothAreRecorded() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId griefer = platform.bans.add("Griefer");

        platform.commands.run("cc-ban", admin, Map.of("player", "Griefer", "length", "7d", "reason", "griefing"));

        Ban ban = platform.bans.bans.get(griefer);
        assertThat(ban.reason()).isEqualTo("griefing");
        assertThat(ban.by()).contains(admin.id());
        assertThat(Duration.between(ban.at(), ban.expires().orElseThrow())).isEqualTo(Duration.ofDays(7));
        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.kind()).isEqualTo(ChangeRecord.Kind.BAN);
        assertThat(record.oldValue()).isEmpty();
        assertThat(record.target()).endsWith(":Griefer");

        platform.commands.run("cc-unban", admin, Map.of("player", "Griefer"));
        assertThat(platform.bans.bans).isEmpty();
        assertThat(platform.changeLog.records.getLast().newValue()).isEmpty();
    }

    @Test
    void anUnbanCanBeRolledBack() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        PlayerId griefer = platform.bans.add("Griefer");
        platform.commands.run("cc-ban", admin, Map.of("player", "Griefer", "length", "permanent", "reason", "-"));
        platform.commands.run("cc-unban", admin, Map.of("player", "Griefer"));

        long unban = platform.changeLog.records.getLast().id();
        platform.commands.run("cc-rollback", admin, Map.of("change", (int) unban));

        assertThat(platform.bans.bans).containsKey(griefer);
        assertThat(platform.bans.bans.get(griefer).expires()).isEmpty();
    }

    @Test
    void unknownPlayersAndBadLengthsAreRefused() {
        runtime.setup();
        runtime.start();
        PlayerInfo admin = platform.players.join("Admin");
        platform.bans.add("Griefer");

        platform.commands.run("cc-ban", admin, Map.of("player", "Nobody", "length", "1d", "reason", "-"));
        platform.commands.run("cc-ban", admin, Map.of("player", "Griefer", "length", "soon", "reason", "-"));

        assertThat(platform.bans.bans).isEmpty();
    }

    @Test
    void bansMadeWithHytalesOwnCommandsAreRecorded() {
        runtime.setup();
        runtime.start();
        runEvery(Duration.ofSeconds(5));
        PlayerId griefer = platform.bans.add("Griefer");

        platform.bans.ban(new Ban(griefer, Optional.empty(), Instant.now(), Optional.empty(), "x"));
        runEvery(Duration.ofSeconds(5));

        ChangeRecord record = platform.changeLog.records.getLast();
        assertThat(record.kind()).isEqualTo(ChangeRecord.Kind.BAN);
        assertThat(record.source()).isEqualTo(ChangeRecord.Source.OUTSIDE);
    }

    private void runEvery(Duration period) {
        platform.scheduler.entries.stream()
                .filter(entry -> period.equals(entry.period))
                .forEach(entry -> entry.work.run());
    }
}
