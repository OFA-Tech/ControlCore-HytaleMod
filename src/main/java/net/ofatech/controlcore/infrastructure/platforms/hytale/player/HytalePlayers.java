package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Collection;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Ids;

public final class HytalePlayers implements Players {

    @Override
    public Optional<PlayerInfo> find(PlayerId id) {
        return Optional.ofNullable(Universe.get().getPlayer(id.value())).map(Ids::player);
    }

    @Override
    public Optional<PlayerInfo> findByName(String name) {
        return Optional.ofNullable(Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE))
                .map(Ids::player);
    }

    @Override
    public Optional<String> language(PlayerId id) {
        return Optional.ofNullable(Universe.get().getPlayer(id.value()))
                .map(player -> player.getLanguage())
                .filter(language -> !language.isBlank());
    }

    @Override
    public Collection<PlayerInfo> online() {
        return Universe.get().getPlayers().stream().map(Ids::player).toList();
    }
}
