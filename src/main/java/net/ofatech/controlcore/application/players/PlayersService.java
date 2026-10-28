package net.ofatech.controlcore.application.players;

import java.util.Optional;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.ports.game.ChatGate;

public final class PlayersService {

    private final PlayersChanges changes;
    private final PlayerGuard guard;
    private final PlayerListing listing;
    private final PlayerModeration moderation;
    private final PlayerAccess access;
    private final PlayerControl control;
    private final BansService bans;
    private final Texts texts;

    public PlayersService(
            PlayersChanges changes,
            PlayerGuard guard,
            PlayerListing listing,
            PlayerModeration moderation,
            PlayerAccess access,
            PlayerControl control,
            BansService bans,
            Texts texts,
            ChatGate chatGate) {
        this.changes = changes;
        this.guard = guard;
        this.listing = listing;
        this.moderation = moderation;
        this.access = access;
        this.control = control;
        this.bans = bans;
        this.texts = texts;
        moderation.listenTo(chatGate);
    }

    public boolean maySee(Optional<PlayerId> who) {
        return guard.maySee(who);
    }

    public void joined(PlayerId player) {
        moderation.joined(player);
        changes.fire();
    }

    public void left(PlayerId player) {
        moderation.left(player);
        changes.fire();
    }

    public PlayersView viewedBy(PlayerId viewer) {
        return new PlayersViewer(
                viewer,
                texts.forPlayer(Optional.of(viewer)),
                guard,
                listing,
                moderation,
                access,
                control,
                bans,
                changes);
    }
}
