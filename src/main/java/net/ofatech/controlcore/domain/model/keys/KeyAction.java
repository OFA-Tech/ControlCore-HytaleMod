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
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * Something a player can run with keys, registered by a mod or created by an admin. Players and admins can rebind
 * it in the control panel's Keymaps tab or with {@code /cc-bind}. Instances are immutable: every builder method
 * returns a changed copy. Register it with {@link net.ofatech.controlcore.api.KeymapsApi#register(KeyAction)}.
 *
 * <pre>{@code
 * KeyAction.of("homes", "go-home")
 *         .displayName(Text.key("homes.keys.go_home", "Go home"))
 *         .command("home {player}")
 *         .defaultKeys(Key.CTRL, Key.NUM_3);
 * }</pre>
 *
 * @param id unique id, {@code modId:action}, for example {@code homes:go-home}
 * @param nameText shown in the Keymaps tab and {@code /cc-keys}
 * @param descriptionText one sentence explaining the action; may be empty
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
        Text nameText,
        Text descriptionText,
        Optional<String> command,
        Optional<Consumer<PlayerId>> onPress,
        KeyBinds defaultKeys,
        boolean blocksGameAction,
        boolean runsAsServer,
        Optional<String> permission) {

    /**
     * Creates a key action; prefer {@link #of(String, String)} and the builder methods. The command is normalized
     * with {@link #normalizeCommand(String)} and a blank permission becomes empty.
     *
     * @param id unique id, {@code modId:action}
     * @param nameText the display name; its English text must not be blank
     * @param descriptionText one sentence explaining the action; may be empty
     * @param command chat command to run, or empty
     * @param onPress code to run, or empty
     * @param defaultKeys default bindings
     * @param blocksGameAction whether the game's own action of the pressed key is cancelled
     * @param runsAsServer whether the command runs as the server console
     * @param permission permission node needed to use the action, or empty for everyone
     * @throws DomainException if an argument is {@code null}, the display name is blank or the command is empty
     */
    public KeyAction {
        Guard.notNull(id, "id");
        Guard.notNull(nameText, "nameText");
        Guard.notBlank(nameText.fallbackText(), "displayName");
        Guard.notNull(descriptionText, "descriptionText");
        Guard.notNull(command, "command");
        Guard.notNull(onPress, "onPress");
        Guard.notNull(defaultKeys, "defaultKeys");
        Guard.notNull(permission, "permission");
        command = command.map(KeyAction::normalizeCommand);
        if (command.isPresent() && command.get().isBlank()) {
            throw new DomainException("The command of key action " + id + " is empty");
        }
        permission = permission.map(String::strip).filter(node -> !node.isEmpty());
    }

    /**
     * Starts a key action; its display name is {@code action} until {@link #displayName} is called. It has no
     * command, no onPress code and no default keys yet, and it cancels the game's own action of the pressed key.
     *
     * @param modId your mod's id, lower case (letters, digits, {@code -}, {@code _}); upper case is lowered
     * @param action the action's name within your mod, lower case (letters, digits, {@code -}, {@code _},
     *     {@code .}); upper case is lowered
     * @return the new key action, with the id {@code modId:action}
     * @throws DomainException if {@code modId} or {@code action} is blank or not a valid id part
     */
    public static KeyAction of(String modId, String action) {
        Guard.notBlank(modId, "modId");
        Guard.notBlank(action, "action");
        SettingId id = new SettingId(
                modId.strip().toLowerCase(Locale.ROOT), action.strip().toLowerCase(Locale.ROOT));
        return new KeyAction(
                id,
                Text.of(id.key()),
                Text.of(""),
                Optional.empty(),
                Optional.empty(),
                KeyBinds.UNBOUND,
                true,
                false,
                Optional.empty());
    }

    /**
     * Sets the name shown to players, in one language for everyone.
     *
     * @param name the display name, for example {@code "Go home"}
     * @return a copy with the new display name
     * @throws DomainException if {@code name} is {@code null} or blank
     */
    public KeyAction displayName(String name) {
        return displayName(Text.of(name));
    }

    /**
     * Sets the name shown to players, translated into each player's language.
     *
     * @param name shown to players in their language, for example {@code Text.key("homes.keys.go_home", "Go home")}
     * @return a copy with the new display name
     * @throws DomainException if {@code name} is {@code null} or its English text is blank
     * @since API 1.5.0
     */
    public KeyAction displayName(Text name) {
        return new KeyAction(
                id, name, descriptionText, command, onPress, defaultKeys, blocksGameAction, runsAsServer, permission);
    }

    /**
     * Sets the description shown to players, in one language for everyone.
     *
     * @param text one sentence explaining the action
     * @return a copy with the new description
     * @throws DomainException if {@code text} is {@code null}
     */
    public KeyAction describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /**
     * Sets the description shown to players, translated into each player's language.
     *
     * @param text one sentence explaining the action, in the player's language
     * @return a copy with the new description
     * @throws DomainException if {@code text} is {@code null}
     * @since API 1.5.0
     */
    public KeyAction describedAs(Text text) {
        return new KeyAction(
                id, nameText, text, command, onPress, defaultKeys, blocksGameAction, runsAsServer, permission);
    }

    /**
     * Returns the display name in English (the fallback text of {@link #nameText}).
     *
     * @return the display name in English
     */
    public String displayName() {
        return nameText.fallbackText();
    }

    /**
     * Returns the description in English (the fallback text of {@link #descriptionText}).
     *
     * @return the description in English; empty if none was set
     */
    public String description() {
        return descriptionText.fallbackText();
    }

    /**
     * Sets the chat command the keys run, as the player unless {@link #runAsServer()} is called.
     *
     * @param text the chat command to run, for example {@code "home {player}"}; a leading {@code /} is removed
     * @return a copy with the new command
     * @throws DomainException if {@code text} is {@code null} or empty after normalizing
     */
    public KeyAction command(String text) {
        Guard.notNull(text, "command");
        return new KeyAction(
                id,
                nameText,
                descriptionText,
                Optional.of(text),
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission);
    }

    /**
     * Sets code that runs when the keys are pressed, in addition to any command.
     *
     * @param action runs with the player who pressed the keys, on a virtual thread; move world work to the
     *     world's thread
     * @return a copy with the new code
     * @throws DomainException if {@code action} is {@code null}
     */
    public KeyAction onPress(Consumer<PlayerId> action) {
        Guard.notNull(action, "onPress");
        return new KeyAction(
                id,
                nameText,
                descriptionText,
                command,
                Optional.of(action),
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission);
    }

    /**
     * Sets the main default combination: the inputs are held together. Alternative combinations are kept.
     *
     * @param inputs the keys or game actions of the combination, for example {@code Key.CTRL, Key.NUM_3}
     * @return a copy with the new main default combination
     * @throws DomainException if {@code inputs} is empty, contains {@code null} or repeats an input
     */
    public KeyAction defaultKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>();
        combos.add(KeyCombo.of(inputs));
        defaultKeys.combos().stream().skip(1).forEach(combos::add);
        return withDefaults(new KeyBinds(combos));
    }

    /**
     * Adds another default combination that also runs the action; adding one that is already there does nothing.
     *
     * @param inputs the keys or game actions held together
     * @return a copy with the extra default combination
     * @throws DomainException if {@code inputs} is empty, contains {@code null} or repeats an input
     */
    public KeyAction alternativeKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>(defaultKeys.combos());
        KeyCombo combo = KeyCombo.of(inputs);
        if (!combos.contains(combo)) {
            combos.add(combo);
        }
        return withDefaults(new KeyBinds(combos));
    }

    /**
     * Sets every default combination at once.
     *
     * @param binds the default bindings; the first combination is the main one
     * @return a copy with the new default bindings
     * @throws DomainException if {@code binds} is {@code null}
     */
    public KeyAction defaultKeys(KeyBinds binds) {
        Guard.notNull(binds, "defaultKeys");
        return withDefaults(binds);
    }

    /**
     * Lets the game also do what the pressed key normally does (by default it is cancelled).
     *
     * @return a copy that does not cancel the game's own action
     */
    public KeyAction keepGameAction() {
        return new KeyAction(
                id, nameText, descriptionText, command, onPress, defaultKeys, false, runsAsServer, permission);
    }

    /**
     * Runs {@link #command} as the server console, with every permission, instead of as the player. Only players
     * with {@link #permission(String)}, or ControlCore's {@code <base>.keys.server} node if none is set, can use
     * it; operators have every node.
     *
     * @return a copy whose command runs as the server
     */
    public KeyAction runAsServer() {
        return new KeyAction(
                id, nameText, descriptionText, command, onPress, defaultKeys, blocksGameAction, true, permission);
    }

    /**
     * Restricts the action to players with a permission node; others do not see it and cannot use it.
     *
     * @param node full permission node a player needs to see and use this action
     * @return a copy that needs the permission
     * @throws DomainException if {@code node} is {@code null} or blank
     */
    public KeyAction permission(String node) {
        Guard.notBlank(node, "permission");
        return new KeyAction(
                id,
                nameText,
                descriptionText,
                command,
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                Optional.of(node));
    }

    /**
     * Returns the mod id part of {@link #id}.
     *
     * @return the mod id, for example {@code homes} for {@code homes:go-home}
     */
    public String modId() {
        return id.namespace();
    }

    /**
     * Returns the command to run for a player, with the placeholders filled in.
     *
     * @param player the player who pressed the keys; fills in {@code {uuid}}
     * @param name the player's name; fills in {@code {player}}
     * @return the command with {@code {player}} and {@code {uuid}} filled in, or empty if the action has no command
     */
    public Optional<String> commandFor(PlayerId player, String name) {
        return command.map(text ->
                text.replace("{player}", name).replace("{uuid}", player.value().toString()));
    }

    /**
     * Cleans up a chat command as typed by a person, for example {@code "/home"} becomes {@code home}.
     *
     * @param text the command text; must not be {@code null}
     * @return {@code text} without surrounding whitespace, one pair of surrounding quotes (people quote commands
     *     with spaces) and the leading {@code /}
     */
    public static String normalizeCommand(String text) {
        String command = text.strip();
        if (command.length() >= 2) {
            char first = command.charAt(0);
            if ((first == '"' || first == '\'') && command.charAt(command.length() - 1) == first) {
                command = command.substring(1, command.length() - 1).strip();
            }
        }
        return command.startsWith("/") ? command.substring(1).strip() : command;
    }

    private KeyAction withDefaults(KeyBinds binds) {
        return new KeyAction(
                id, nameText, descriptionText, command, onPress, binds, blocksGameAction, runsAsServer, permission);
    }
}
