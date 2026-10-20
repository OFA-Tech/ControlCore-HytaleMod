package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.CommandRunner;

/** Records commands and runs background work at once. */
public final class RecordingCommandRunner implements CommandRunner {

    public record Run(PlayerId player, String command) {}

    public final List<Run> runs = new CopyOnWriteArrayList<>();

    @Override
    public void runAs(PlayerId player, String command) {
        runs.add(new Run(player, command));
    }

    @Override
    public void runInBackground(String description, Runnable work) {
        work.run();
    }
}
