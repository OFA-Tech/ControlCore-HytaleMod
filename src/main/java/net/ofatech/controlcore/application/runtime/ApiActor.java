package net.ofatech.controlcore.application.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.ofatech.controlcore.application.history.ChangeContext;
import net.ofatech.controlcore.application.players.PlayerDirectory;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

record ApiActor(Optional<PlayerId> who, Actor actor) {

    static final ApiActor MOD = new ApiActor(Optional.empty(), Actor.MOD);

    static ApiActor of(PlayerId player, PlayerDirectory directory) {
        Objects.requireNonNull(player, "player");
        return new ApiActor(Optional.of(player), Actor.player(player, directory.nameOf(player)));
    }

    boolean plain() {
        return who.isEmpty();
    }

    boolean self(Optional<PlayerId> player) {
        return plain() || player.isEmpty() || player.equals(who);
    }

    <T> T run(Supplier<T> work) {
        return plain() ? work.get() : ChangeContext.as(new ChangeContext.Attribution(actor, Source.API, 0), work);
    }

    static <T> Result<T> forbidden(String code, String description) {
        return Result.failure(Error.forbidden(code, description));
    }
}
