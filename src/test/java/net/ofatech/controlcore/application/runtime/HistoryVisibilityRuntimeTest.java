package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Kind;
import net.ofatech.controlcore.domain.model.mods.ModDependency;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.result.Result;
import org.junit.jupiter.api.Test;

class HistoryVisibilityRuntimeTest extends RuntimeTestBase {

    @Test
    void aViewerWhoIsNotAdminSeesOnlyRowsWhoseTargetTheyCanSee() {
        platform.modCatalog.add("Test:Base", false);
        seed(1, Kind.SETTING, "ghost:gone");
        seed(2, Kind.MOD, "Gone:Mod");
        seed(3, Kind.PERMISSION, "setting:server:server-name");
        seed(4, Kind.PERMISSION, "category:controlcore");
        seed(5, Kind.MOD, "Test:Base");
        runtime.setup();
        runtime.start();
        PlayerInfo builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));
        HistoryApi history = ControlCoreApi.get().history();

        assertThat(history.list(Optional.empty(), "", 0)).hasSize(5);
        assertThat(history.actingFor(builder.id()).list(Optional.empty(), "", 0))
                .extracting(HistoryApi.Change::target)
                .containsExactlyInAnyOrder("Test:Base", "category:controlcore");
    }

    @Test
    void aModAlreadyInTheWantedStateSaysSoBeforeCheckingWhatElseWouldChange() {
        platform.modCatalog.add("OFATech:ControlCore", false);
        platform.modCatalog.setEnabled("OFATech:ControlCore", false);
        platform.modCatalog.add(
                "Test:Needy", false, new ModDependency("OFATech:ControlCore", "", false, ModDependency.Status.OK));
        runtime.setup();
        runtime.start();
        PlayerInfo builder = platform.players.join("Builder");
        platform.deniedNodesFor.put(builder.id(), List.of("settings", "admin"));

        Result<Reply> on = platform.commands.runAsConsole("cc-mod-on", Map.of("mod", "Test:Needy", "confirm", "check"));
        assertThat(on.isSuccess()).as("%s", on).isTrue();
        assertThat(on.value().text().orElseThrow()).isEqualTo("Needy is already on after the next restart.");

        Result<Reply> forbidden =
                platform.commands.run("cc-mod-on", builder, Map.of("mod", "Test:Needy", "confirm", "check"));
        assertThat(forbidden.isFailure()).as("%s", forbidden).isTrue();
        assertThat(forbidden.error().code()).isEqualTo("Mods.Forbidden");
    }

    private void seed(long id, Kind kind, String target) {
        platform.changeLog.records.add(new ChangeRecord(
                id,
                platform.clock.now(),
                Actor.console("Console"),
                ChangeRecord.Source.COMMAND,
                kind,
                target,
                "a",
                "b",
                false,
                0));
    }
}
