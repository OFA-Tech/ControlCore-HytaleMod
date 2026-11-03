package net.ofatech.controlcore.application.runtime;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.api.HistoryApi;
import net.ofatech.controlcore.application.history.HistoryCommand;
import net.ofatech.controlcore.application.history.HistoryEditor;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(HistoryApi.class)
public final class HistoryApiService implements HistoryApi {

    private final HistoryEditor editor;
    private final Texts texts;
    private final PlayerDirectory directory;
    private final ApiActor actor;

    public HistoryApiService(HistoryEditor editor, Texts texts, PlayerDirectory directory) {
        this(editor, texts, directory, ApiActor.MOD);
    }

    private HistoryApiService(HistoryEditor editor, Texts texts, PlayerDirectory directory, ApiActor actor) {
        this.editor = editor;
        this.texts = texts;
        this.directory = directory;
        this.actor = actor;
    }

    @Override
    public HistoryApi actingFor(PlayerId player) {
        return new HistoryApiService(editor, texts, directory, ApiActor.of(player, directory));
    }

    @Override
    public List<Change> list(Optional<Kind> kind, String search, int limit) {
        if (!editor.maySee(actor.who())) {
            return List.of();
        }
        String filter = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return editor.listed(tr(), actor.who()).stream()
                .filter(listed -> kind.isEmpty()
                        || listed.entry().kind().name().equals(kind.get().name()))
                .filter(listed -> HistoryCommand.matches(listed.entry(), filter))
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .map(listed -> change(listed.entry(), listed.record()))
                .toList();
    }

    @Override
    public Result<String> rollback(long id) {
        return actor.run(() -> editor.rollback(id, actor.who(), actor.actor(), tr()));
    }

    @Override
    public Result<String> clearOlderThan(int days) {
        return editor.clear(Optional.of(Duration.ofDays(Math.max(0, days))), actor.who(), tr());
    }

    @Override
    public Result<String> clearAll() {
        return editor.clear(Optional.empty(), actor.who(), tr());
    }

    private Translator tr() {
        return texts.forPlayer(actor.who());
    }

    private static Change change(HistoryView.Entry entry, ChangeRecord record) {
        return new Change(
                entry.id(),
                record.at(),
                Kind.valueOf(record.kind().name()),
                entry.target(),
                record.actor().player(),
                entry.who(),
                Source.valueOf(record.source().name()),
                entry.what(),
                entry.before(),
                entry.after(),
                entry.restart(),
                entry.rollback(),
                entry.rolledBack(),
                entry.canRollback());
    }
}
