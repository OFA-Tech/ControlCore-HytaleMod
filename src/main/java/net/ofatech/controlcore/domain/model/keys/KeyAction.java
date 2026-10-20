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
 * Something a player can run with keys, registered by a mod. Players and admins can rebind it in the control
 * panel's Keymaps tab or with {@code /cc-bind}.
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
 * @param command chat command run as the player (their permissions apply), without or with {@code /};
 *     {@code {player}} and {@code {uuid}} are filled in
 * @param onPress code the mod runs when the keys are pressed; called on a virtual thread, so move world work to
 *     the world's thread
 * @param defaultKeys bindings until a player or admin changes them; the first is the main one
 * @param blocksGameAction whether the game's own action of the pressed key is cancelled (for example ability 1
 *     for Q). Hotbar changes are never cancelled
 */
public record KeyAction(
        SettingId id,
        String displayName,
        String description,
        Optional<String> command,
        Optional<Consumer<PlayerId>> onPress,
        KeyBinds defaultKeys,
        boolean blocksGameAction) {

    public KeyAction {
        Guard.notNull(id, "id");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(description, "description");
        Guard.notNull(command, "command");
        Guard.notNull(onPress, "onPress");
        Guard.notNull(defaultKeys, "defaultKeys");
        command = command.map(String::strip).map(text -> text.startsWith("/") ? text.substring(1) : text);
        if (command.isPresent() && command.get().isBlank()) {
            throw new DomainException("The command of key action " + id + " is empty");
        }
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
        return new KeyAction(id, id.key(), "", Optional.empty(), Optional.empty(), KeyBinds.UNBOUND, true);
    }

    public KeyAction displayName(String name) {
        return new KeyAction(id, name, description, command, onPress, defaultKeys, blocksGameAction);
    }

    public KeyAction describedAs(String text) {
        return new KeyAction(id, displayName, text, command, onPress, defaultKeys, blocksGameAction);
    }

    /** @param text the chat command to run as the player, for example {@code "home {player}"} */
    public KeyAction command(String text) {
        Guard.notNull(text, "command");
        return new KeyAction(id, displayName, description, Optional.of(text), onPress, defaultKeys, blocksGameAction);
    }

    /** @param action runs when the keys are pressed, on a virtual thread */
    public KeyAction onPress(Consumer<PlayerId> action) {
        Guard.notNull(action, "onPress");
        return new KeyAction(id, displayName, description, command, Optional.of(action), defaultKeys, blocksGameAction);
    }

    /** Sets the main default combination: the inputs are held together. */
    public KeyAction defaultKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>();
        combos.add(KeyCombo.of(inputs));
        defaultKeys.combos().stream().skip(1).forEach(combos::add);
        return withDefaults(combos);
    }

    /** Adds another default combination that also runs the action. */
    public KeyAction alternativeKeys(KeyInput... inputs) {
        List<KeyCombo> combos = new ArrayList<>(defaultKeys.combos());
        KeyCombo combo = KeyCombo.of(inputs);
        if (!combos.contains(combo)) {
            combos.add(combo);
        }
        return withDefaults(combos);
    }

    /** Lets the game also do what the pressed key normally does (by default it is cancelled). */
    public KeyAction keepGameAction() {
        return new KeyAction(id, displayName, description, command, onPress, defaultKeys, false);
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

    private KeyAction withDefaults(List<KeyCombo> combos) {
        return new KeyAction(id, displayName, description, command, onPress, new KeyBinds(combos), blocksGameAction);
    }
}
