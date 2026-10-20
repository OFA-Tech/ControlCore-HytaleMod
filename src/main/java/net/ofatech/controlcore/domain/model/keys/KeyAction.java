package net.ofatech.controlcore.domain.model.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.guard.DomainException;
import net.ofatech.controlcore.domain.guard.Guard;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingId;

/**
 * Something a player can run with keys, registered by a mod or created by an admin. Players and admins can rebind
 * it in the control panel's Keymaps tab or with {@code /cc-bind}.
 *
 * <pre>{@code
 * KeyAction.of("homes", "go-home")
 *         .displayName("Go home")
 *         .command("home {player}")
 *         .defaultKeys(Key.CTRL, Key.NUM_3);
 * }</pre>
 *
 * @param id unique id, {@code modId:action}
 * @param displayName shown in the Keymaps tab and {@code /cc-keys}
 * @param description one sentence explaining the action; may be empty
 * @param command chat command, without or with {@code /}; {@code {player}} and {@code {uuid}} are filled in with
 *     the player who pressed the keys
 * @param onPress code the mod runs when the keys are pressed; called on a virtual thread, so move world work to
 *     the world's thread
 * @param defaultKeys bindings until a player or admin changes them; the first is the main one
 * @param blocksGameAction whether the game's own action of the pressed key is cancelled (for example ability 1
 *     for Q). Hotbar changes are never cancelled
 * @param runsAsServer whether {@code command} runs as the server console (every permission) instead of as the
 *     player; only players with {@link #permission} (or ControlCore's {@code keys.server} node) can use it
 * @param permission full permission node a player needs to see and use the action; empty means everyone (for
 *     server-run actions: ControlCore's {@code <base>.keys.server})
 */
public record KeyAction(
        SettingId id,
        String displayName,
        String description,
        Optional<String> command,
        Optional<Consumer<PlayerId>> onPress,
        KeyBinds defaultKeys,
        boolean blocksGameAction,
        boolean runsAsServer,
        Optional<String> permission) {

    public KeyAction {
        Guard.notNull(id, "id");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(description, "description");
        Guard.notNull(command, "command");
        Guard.notNull(onPress, "onPress");
        Guard.notNull(defaultKeys, "defaultKeys");
        Guard.notNull(permission, "permission");
        command = command.map(String::strip)
                .map(text -> text.startsWith("/") ? text.substring(1).strip() : text);
        if (command.isPresent() && command.get().isBlank()) {
            throw new DomainException("The command of key action " + id + " is empty");
        }
        permission = permission.map(String::strip).filter(node -> !node.isEmpty());
    }

    /**
     * Starts a key action; its display name is {@code action} until {@link #displayName} is called.
     *
     * @param modId your mod's id, lower case (letters, digits, {@code -}, {@code _})
     * @param action the action's name within your mod, lower case (letters, digits, {@code -}, {@code _},
     *     {@code .})
     */
    public static KeyAction of(String modId, String action) {
        Guard.notBlank(modId, "modId");
        Guard.notBlank(action, "action");
        SettingId id = new SettingId(
                modId.strip().toLowerCase(Locale.ROOT), action.strip().toLowerCase(Locale.ROOT));
        return new KeyAction(
                id, id.key(), "", Optional.empty(), Optional.empty(), KeyBinds.UNBOUND, true, false, Optional.empty());
    }

    public KeyAction displayName(String name) {
        return new KeyAction(
                id, name, description, command, onPress, defaultKeys, blocksGameAction, runsAsServer, permission);
    }

    public KeyAction describedAs(String text) {
        return new KeyAction(
                id, displayName, text, command, onPress, defaultKeys, blocksGameAction, runsAsServer, permission);
    }

    /** @param text the chat command to run, for example {@code "home {player}"} */
    public KeyAction command(String text) {
        Guard.notNull(text, "command");
        return new KeyAction(
                id,
                displayName,
                description,
                Optional.of(text),
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission);
    }

    /** @param action runs when the keys are pressed, on a virtual thread */
    public KeyAction onPress(Consumer<PlayerId> action) {
        Guard.notNull(action, "onPress");
        return new KeyAction(
                id,
                displayName,
                description,
                command,
                Optional.of(action),
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission);
    }

    /** Sets the main default combination: the inputs are held together. */
    public KeyAction defaultKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>();
        combos.add(KeyCombo.of(inputs));
        defaultKeys.combos().stream().skip(1).forEach(combos::add);
        return withDefaults(new KeyBinds(combos));
    }

    /** Adds another default combination that also runs the action. */
    public KeyAction alternativeKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>(defaultKeys.combos());
        KeyCombo combo = KeyCombo.of(inputs);
        if (!combos.contains(combo)) {
            combos.add(combo);
        }
        return withDefaults(new KeyBinds(combos));
    }

    /** Sets every default combination at once. */
    public KeyAction defaultKeys(KeyBinds binds) {
        Guard.notNull(binds, "defaultKeys");
        return withDefaults(binds);
    }

    /** Lets the game also do what the pressed key normally does (by default it is cancelled). */
    public KeyAction keepGameAction() {
        return new KeyAction(
                id, displayName, description, command, onPress, defaultKeys, false, runsAsServer, permission);
    }

    /**
     * Runs {@link #command} as the server console, with every permission, instead of as the player. Only players
     * with {@link #permission(String)}, or ControlCore's {@code <base>.keys.server} node if none is set, can use
     * it; operators have every node.
     */
    public KeyAction runAsServer() {
        return new KeyAction(
                id, displayName, description, command, onPress, defaultKeys, blocksGameAction, true, permission);
    }

    /** @param node full permission node a player needs to see and use this action */
    public KeyAction permission(String node) {
        Guard.notBlank(node, "permission");
        return new KeyAction(
                id,
                displayName,
                description,
                command,
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                Optional.of(node));
    }

    /** @return the mod id part of {@link #id} */
    public String modId() {
        return id.namespace();
    }

    /** @return the command with {@code {player}} and {@code {uuid}} filled in, if the action has one */
    public Optional<String> commandFor(PlayerId player, String name) {
        return command.map(text ->
                text.replace("{player}", name).replace("{uuid}", player.value().toString()));
    }

    private KeyAction withDefaults(KeyBinds binds) {
        return new KeyAction(
                id, displayName, description, command, onPress, binds, blocksGameAction, runsAsServer, permission);
    }
}
