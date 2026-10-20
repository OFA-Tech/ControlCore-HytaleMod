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
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.keys.KeyBinds;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Registered key actions and their bindings. A player's own bindings (in {@link PlayerData}) win over the server's
 * (in {@link KeyBindStore}), which win over the action's defaults. Bindings are stored as text, so they survive the
 * action being unregistered and keys the server cannot detect yet. Admins' own actions ({@code custom:*}) are kept
 * in {@link CustomKeyBindStore}.
 */
@AutoService(KeymapsApi.class)
public final class KeymapRegistry implements KeymapsApi {

    static final String PLAYER_KEY_PREFIX = "keybind:";
    /** Namespace of the actions admins create. */
    public static final String CUSTOM = "custom";
    /** ControlCore node (relative to its base permission) needed for server-run actions without their own node. */
    public static final String SERVER_KEYS_NODE = "keys.server";

    private final KeyBindStore serverBinds;
    private final CustomKeyBindStore customBinds;
    private final PlayerData playerData;
    private final Permissions permissions;
    private final Log log;
    private final Map<SettingId, KeyAction> actions = new ConcurrentHashMap<>();
    private final List<Consumer<? super SettingChanged>> listeners = new CopyOnWriteArrayList<>();

    public KeymapRegistry(
            KeyBindStore serverBinds,
            CustomKeyBindStore customBinds,
            PlayerData playerData,
            Permissions permissions,
            Log log) {
        this.serverBinds = serverBinds;
        this.customBinds = customBinds;
        this.playerData = playerData;
        this.permissions = permissions;
        this.log = log;
    }

    @Override
    public Result<KeyAction> register(KeyAction action) {
        if (action != null && CUSTOM.equals(action.modId())) {
            return Result.failure(Error.validation(
                    "Keys.ReservedId", "The namespace '" + CUSTOM + "' is for key actions admins create."));
        }
        return put(action);
    }

    @Override
    public void unregister(SettingId id) {
        if (id != null && !CUSTOM.equals(id.namespace())) {
            actions.remove(id);
        }
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

    /** @return the actions {@code player} may see and use, sorted by id */
    public List<KeyAction> actionsFor(PlayerId player) {
        return actions().stream().filter(action -> canUse(action, player)).toList();
    }

    /**
     * @return whether {@code player} may see and use {@code action}: they need its permission if it has one, and
     *     ControlCore's {@code keys.server} node for a server-run action without one
     */
    public boolean canUse(KeyAction action, PlayerId player) {
        if (action.permission().isPresent()) {
            return permissions.has(player, action.permission().get());
        }
        return !action.runsAsServer() || permissions.hasOwn(player, SERVER_KEYS_NODE);
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
        return usable(id, player).flatMap(action -> KeyBinds.parse(keys)).map(binds -> {
            String before = keysFor(id, player).toString();
            playerData.put(player, PLAYER_KEY_PREFIX + id, binds.toString());
            playerData.flush();
            changed(id, player, before, binds.toString());
            return binds;
        });
    }

    @Override
    public Result<KeyBinds> resetBind(SettingId id, PlayerId player) {
        return usable(id, player).map(action -> {
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

    /** Registers the actions admins created earlier; call once at startup. */
    public void loadCustom() {
        for (CustomKeyBind bind : customBinds.all()) {
            customAction(bind.name(), bind.keys(), bind.command(), bind.runAsServer())
                    .flatMap(this::put)
                    .onFailure(error ->
                            log.warn("Ignoring custom key bind '" + bind.name() + "': " + error.description()));
        }
    }

    /**
     * Creates or replaces an admin's own key action {@code custom:<name>}.
     *
     * @param name lower case letters, digits, {@code -}, {@code _} and {@code .}; {@code custom:} may be included
     * @param keys default keys, for example {@code ctrl+f}
     * @param command the command, with or without {@code /}
     * @param runAsServer whether the command runs as the server console
     * @return the action, or {@code Keys.InvalidName}, {@code Keys.InvalidCombo} or {@code Keys.NothingToRun}
     */
    public Result<KeyAction> addCustom(String name, String keys, String command, boolean runAsServer) {
        return customAction(name, keys, command, runAsServer)
                .flatMap(action -> put(action).map(registered -> {
                    customBinds.put(new CustomKeyBind(
                            action.id().key(),
                            action.defaultKeys().toString(),
                            action.command().orElseThrow(),
                            runAsServer));
                    return registered;
                }));
    }

    /**
     * Removes an admin's own key action and the server's bindings for it.
     *
     * @return the removed action, or {@code Keys.UnknownAction}
     */
    public Result<KeyAction> removeCustom(String name) {
        return customId(name).flatMap(this::known).map(action -> {
            actions.remove(action.id());
            customBinds.remove(action.id().key());
            serverBinds.remove(action.id());
            serverBinds.flush();
            return action;
        });
    }

    /** @return the saved admin bind with that name ({@code custom:} optional) */
    public Optional<CustomKeyBind> findCustom(String name) {
        Result<SettingId> parsed = customId(name);
        return Optional.of(parsed)
                .filter(Result::isSuccess)
                .map(Result::value)
                .flatMap(id -> customBinds.all().stream()
                        .filter(bind -> bind.name().equals(id.key()))
                        .findFirst());
    }

    /** Listens to changes of players' own bindings, reported as player-scoped {@link SettingChanged} events. */
    public Subscription onChange(Consumer<? super SettingChanged> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private Result<KeyAction> put(KeyAction action) {
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

    private Result<KeyAction> customAction(String name, String keys, String command, boolean runAsServer) {
        if (command == null || KeyAction.normalizeCommand(command).isBlank()) {
            return Result.failure(Error.validation("Keys.NothingToRun", "Write the command the keys should run."));
        }
        return customId(name)
                .flatMap(id -> KeyBinds.parse(keys).map(binds -> {
                    KeyAction action = KeyAction.of(CUSTOM, id.key())
                            .displayName(id.key())
                            .describedAs("Added by an admin.")
                            .command(command)
                            .defaultKeys(binds);
                    return runAsServer ? action.runAsServer() : action;
                }));
    }

    private static Result<SettingId> customId(String name) {
        String text = name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith(CUSTOM + ":")) {
            text = text.substring(CUSTOM.length() + 1);
        }
        Result<SettingId> id = SettingId.parse(CUSTOM + ":" + text);
        return id.isSuccess()
                ? id
                : Result.failure(Error.validation(
                        "Keys.InvalidName",
                        "'" + text + "' is not a valid name: use lower case letters, digits, - and _."));
    }

    private Result<KeyAction> known(SettingId id) {
        return find(id).map(Result::success)
                .orElseGet(() -> Result.failure(Error.notFound(
                        "Keys.UnknownAction", "There is no key action '" + id + "'. See /cc-keys for the list.")));
    }

    private Result<KeyAction> usable(SettingId id, PlayerId player) {
        return known(id)
                .flatMap(action -> canUse(action, player)
                        ? Result.success(action)
                        : Result.failure(Error.notFound(
                                "Keys.UnknownAction",
                                "There is no key action '" + id + "'. See /cc-keys for the list.")));
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
