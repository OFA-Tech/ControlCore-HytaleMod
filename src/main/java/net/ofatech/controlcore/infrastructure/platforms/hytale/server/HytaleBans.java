package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.auth.ProfileServiceClient;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.Bans;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleBans implements Bans {

    private final Log log;

    public HytaleBans(Log log) {
        this.log = log;
    }

    @Override
    public List<Ban> all() {
        try {
            return AccessControlModule.get().getBanProvider().getBans().stream()
                    .map(ban -> new Ban(
                            PlayerId.of(ban.getTarget()),
                            Optional.ofNullable(ban.getBy())
                                    .filter(by -> !by.equals(ConsoleSender.INSTANCE.getUuid()))
                                    .map(PlayerId::of),
                            ban.getTimestamp(),
                            Optional.ofNullable(ban.getExpiresOn()),
                            ban.getReason()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Could not read the ban list: " + e);
            return List.of();
        }
    }

    @Override
    public boolean ban(Ban ban) {
        return AccessControlModule.get()
                .ban(new com.hypixel.hytale.server.core.modules.accesscontrol.ban.Ban(
                        ban.target().value(),
                        ban.by().map(PlayerId::value).orElse(ConsoleSender.INSTANCE.getUuid()),
                        ban.at(),
                        ban.expires().orElse(null),
                        ban.reason()));
    }

    @Override
    public boolean unban(PlayerId target) {
        return AccessControlModule.get().unban(target.value());
    }

    @Override
    public CompletableFuture<Optional<Profile>> lookup(String nameOrId) {
        String text = nameOrId == null ? "" : nameOrId.strip();
        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        PlayerRef online = Universe.get().getPlayer(text, NameMatching.EXACT_IGNORE_CASE);
        if (online != null) {
            return CompletableFuture.completedFuture(
                    Optional.of(new Profile(PlayerId.of(online.getUuid()), online.getUsername())));
        }
        Optional<UUID> id = uuid(text);
        Optional<ProfileServiceClient> client = client();
        String token = token();
        if (client.isEmpty() || token == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<ProfileServiceClient.PublicGameProfile> found = id.isPresent()
                ? client.get().getProfileByUuidAsync(id.get(), token)
                : client.get().getProfileByUsernameAsync(text, token);
        return found.handle((profile, failure) -> {
            if (failure != null
                    || profile == null
                    || profile.getUuid() == null
                    || profile.getUsername() == null
                    || profile.getUsername().isBlank()) {
                return Optional.<Profile>empty();
            }
            return Optional.of(new Profile(PlayerId.of(profile.getUuid()), profile.getUsername()));
        });
    }

    @Override
    public CompletableFuture<Optional<String>> nameOf(PlayerId id) {
        PlayerRef online = Universe.get().getPlayer(id.value());
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUsername()));
        }
        return savedName(id)
                .thenCompose(saved -> saved.isPresent() ? CompletableFuture.completedFuture(saved) : accountName(id));
    }

    private static CompletableFuture<Optional<String>> savedName(PlayerId id) {
        try {
            PlayerStorage storage = Universe.get().getPlayerStorage();
            if (storage == null || !storage.getPlayers().contains(id.value())) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return storage.load(id.value()).handle((holder, failure) -> {
                if (failure != null || holder == null) {
                    return Optional.<String>empty();
                }
                Nameplate nameplate = holder.getComponent(Nameplate.getComponentType());
                return nameplate == null
                                || nameplate.getText() == null
                                || nameplate.getText().isBlank()
                        ? Optional.<String>empty()
                        : Optional.of(nameplate.getText());
            });
        } catch (IOException | RuntimeException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static CompletableFuture<Optional<String>> accountName(PlayerId id) {
        Optional<ProfileServiceClient> client = client();
        String token = token();
        if (client.isEmpty() || token == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return client.get()
                .getProfileByUuidAsync(id.value(), token)
                .handle((profile, failure) -> failure != null || profile == null
                        ? Optional.<String>empty()
                        : Optional.ofNullable(profile.getUsername()));
    }

    private static Optional<ProfileServiceClient> client() {
        try {
            return Optional.ofNullable(ServerAuthManager.getInstance().getProfileServiceClient());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String token() {
        try {
            return ServerAuthManager.getInstance().getSessionToken();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Optional<UUID> uuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
