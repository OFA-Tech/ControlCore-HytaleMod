package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Registered key actions and their bindings. A player's own bindings (in {@link PlayerData}) win over the server's
 * (in {@link KeyBindStore}), which win over the mod's defaults. Bindings are stored as text, so they survive the
 * action being unregistered and keys the server cannot detect yet.
 */
@AutoService(KeymapsApi.class)
public final class KeymapRegistry implements KeymapsApi {

    static final String PLAYER_KEY_PREFIX = "keybind:";

    private final KeyBindStore serverBinds;
    private final PlayerData playerData;
    private final Log log;
    private final Map<SettingId, KeyAction> actions = new ConcurrentHashMap<>();
    private final List<Consumer<? super SettingChanged>> listeners = new CopyOnWriteArrayList<>();

    public KeymapRegistry(KeyBindStore serverBinds, PlayerData playerData, Log log) {
        this.serverBinds = serverBinds;
        this.playerData = playerData;
        this.log = log;
    }

    @Override
    public Result<KeyAction> register(KeyAction action) {
        if (action == null) {
            return Result.failure(Error.validation("Keys.NothingToRun", "The key action is missing."));
        }
        if (action.command().isEmpty() && action.onPress().isEmpty()) {
            return Result.failure(Error.validation(
                    "Keys.NothingToRun", "Key action " + action.id() + " needs a command or onPress."));
        }
        actions.put(action.id(), action);
        log.debug("Registered key action " + action.id() + " (" + action.defaultKeys() + ")");
        return Result.success(action);
    }

    @Override
    public void unregister(SettingId id) {
        actions.remove(id);
    }

    @Override
    public Optional<KeyAction> find(SettingId id) {
        return Optional.ofNullable(id).map(actions::get);
    }

    @Override
    public List<KeyAction> actions() {
        return actions.values().stream()
                .sorted(Comparator.comparing(KeyAction::id))
                .toList();
    }

    @Override
    public KeyBinds keysFor(SettingId id, PlayerId player) {
        return ownKeys(id, player).orElseGet(() -> serverKeys(id));
    }

    @Override
    public KeyBinds serverKeys(SettingId id) {
        return serverBinds.get(id).flatMap(text -> parsed(id, text)).orElseGet(() -> defaults(id));
    }

    /** @return the bindings the player chose themselves, if any */
    public Optional<KeyBinds> ownKeys(SettingId id, PlayerId player) {
        return playerData.get(player, PLAYER_KEY_PREFIX + id).flatMap(text -> parsed(id, text));
    }

    @Override
    public Result<KeyBinds> bind(SettingId id, PlayerId player, String keys) {
        return known(id).flatMap(action -> KeyBinds.parse(keys)).map(binds -> {
            String before = keysFor(id, player).toString();
            playerData.put(player, PLAYER_KEY_PREFIX + id, binds.toString());
            playerData.flush();
            changed(id, player, before, binds.toString());
            return binds;
        });
    }

    @Override
    public Result<KeyBinds> resetBind(SettingId id, PlayerId player) {
        return known(id).map(action -> {
            String before = keysFor(id, player).toString();
            playerData.remove(player, PLAYER_KEY_PREFIX + id);
            playerData.flush();
            KeyBinds after = keysFor(id, player);
            changed(id, player, before, after.toString());
            return after;
        });
    }

    @Override
    public Result<KeyBinds> bindServer(SettingId id, String keys) {
        return known(id).flatMap(action -> KeyBinds.parse(keys)).map(binds -> {
            serverBinds.put(id, binds.toString());
            serverBinds.flush();
            return binds;
        });
    }

    @Override
    public Result<KeyBinds> resetServerBind(SettingId id) {
        return known(id).map(action -> {
            serverBinds.remove(id);
            serverBinds.flush();
            return defaults(id);
        });
    }

    /** Listens to changes of players' own bindings, reported as player-scoped {@link SettingChanged} events. */
    public Subscription onChange(Consumer<? super SettingChanged> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private Result<KeyAction> known(SettingId id) {
        return find(id).map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Keys.UnknownAction", "There is no key action '" + id + "'. See /cc-keys for the list.")));
    }

    private KeyBinds defaults(SettingId id) {
        return find(id).map(KeyAction::defaultKeys).orElse(KeyBinds.UNBOUND);
    }

    private Optional<KeyBinds> parsed(SettingId id, String text) {
        Result<KeyBinds> binds = KeyBinds.parse(text);
        if (binds.isFailure()) {
            log.warn("Ignoring saved keys '" + text + "' of " + id + ": "
                    + binds.error().description());
            return Optional.empty();
        }
        return Optional.of(binds.value());
    }

    private void changed(SettingId id, PlayerId player, String before, String after) {
        SettingChanged event = new SettingChanged(id, SettingScope.PLAYER, Optional.of(player), before, after);
        for (Consumer<? super SettingChanged> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                log.error("A key binding listener failed", e);
            }
        }
    }
}
