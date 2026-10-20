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
import net.ofatech.controlcore.domain.model.settings.SettingLabels;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * Something a player can run with keys, registered by a mod or created by an admin. Players and admins can rebind
 * it in the control panel's Keymaps tab or with {@code /cc-bind}.
 *
 * <pre>{@code
 * KeyAction.of("homes", "go-home")
 *         .displayName(Text.key("homes.keys.go_home", "Go home"))
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
 * @param labels translation keys of the display name and description (since API 1.5.0)
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
        Optional<String> permission,
        SettingLabels labels) {

    public KeyAction {
        Guard.notNull(id, "id");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(description, "description");
        Guard.notNull(command, "command");
        Guard.notNull(onPress, "onPress");
        Guard.notNull(defaultKeys, "defaultKeys");
        Guard.notNull(permission, "permission");
        Guard.notNull(labels, "labels");
        command = command.map(KeyAction::normalizeCommand);
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
                id,
                id.key(),
                "",
                Optional.empty(),
                Optional.empty(),
                KeyBinds.UNBOUND,
                true,
                false,
                Optional.empty(),
                SettingLabels.NONE);
    }

    public KeyAction displayName(String name) {
        return displayName(Text.of(name));
    }

    /**
     * @param name shown to players in their language
     * @since API 1.5.0
     */
    public KeyAction displayName(Text name) {
        return new KeyAction(
                id,
                name.fallbackText(),
                description,
                command,
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission,
                new SettingLabels(name.key(), labels.description(), Optional.empty(), Optional.empty()));
    }

    public KeyAction describedAs(String text) {
        return describedAs(Text.of(text));
    }

    /**
     * @param text one sentence explaining the action, in the player's language
     * @since API 1.5.0
     */
    public KeyAction describedAs(Text text) {
        return new KeyAction(
                id,
                displayName,
                text.fallbackText(),
                command,
                onPress,
                defaultKeys,
                blocksGameAction,
                runsAsServer,
                permission,
                new SettingLabels(labels.name(), text.key(), Optional.empty(), Optional.empty()));
    }

    /** @return the display name, translated where the action has a key (since API 1.5.0) */
    public Text nameText() {
        return labels.name().map(key -> Text.key(key, displayName)).orElse(Text.of(displayName));
    }

    /** @return the description, translated where the action has a key (since API 1.5.0) */
    public Text descriptionText() {
        return labels.description().map(key -> Text.key(key, description)).orElse(Text.of(description));
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
                permission,
                labels);
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
                permission,
                labels);
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
                id, displayName, description, command, onPress, defaultKeys, false, runsAsServer, permission, labels);
    }

    /**
     * Runs {@link #command} as the server console, with every permission, instead of as the player. Only players
     * with {@link #permission(String)}, or ControlCore's {@code <base>.keys.server} node if none is set, can use
     * it; operators have every node.
     */
    public KeyAction runAsServer() {
        return new KeyAction(
                id,
                displayName,
                description,
                command,
                onPress,
                defaultKeys,
                blocksGameAction,
                true,
                permission,
                labels);
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
                Optional.of(node),
                labels);
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

    /**
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
                id,
                displayName,
                description,
                command,
                onPress,
                binds,
                blocksGameAction,
                runsAsServer,
                permission,
                labels);
    }
}
