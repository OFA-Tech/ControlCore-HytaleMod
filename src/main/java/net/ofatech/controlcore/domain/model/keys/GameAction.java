package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;

/**
 * A game action, whatever key the player has it on. Using actions instead of {@link Key}s keeps a binding working
 * when a player rebinds keys in their client settings. Actions the client does not report to servers can be bound
 * but cannot trigger yet; see {@link KeyCombo#trigger()}.
 */
public enum GameAction implements KeyInput {
    /** Attack / primary action; left mouse button by default. */
    ATTACK("Attack", KeySignal.PRIMARY),
    /** Secondary action; right mouse button by default. */
    SECONDARY_ACTION("Secondary action", KeySignal.SECONDARY),
    /** Ability 1; Q by default. */
    ABILITY_1("Ability 1", KeySignal.ABILITY_1),
    /** Ability 2; E by default. */
    ABILITY_2("Ability 2", KeySignal.ABILITY_2),
    /** Ability 3; R by default. */
    ABILITY_3("Ability 3", KeySignal.ABILITY_3),
    /** Ability 4. */
    ABILITY_4("Ability 4", KeySignal.ABILITY_4),
    /** Dodge. */
    DODGE("Dodge", KeySignal.DODGE),
    /** Interact / use; F by default. */
    INTERACT("Interact", KeySignal.USE),
    /** Pick block; middle mouse button by default. */
    PICK_BLOCK("Pick block", KeySignal.PICK),
    /** Select hotbar slot 1; 1 by default. */
    HOTBAR_1("Hotbar 1", KeySignal.HOTBAR_1),
    /** Select hotbar slot 2; 2 by default. */
    HOTBAR_2("Hotbar 2", KeySignal.HOTBAR_2),
    /** Select hotbar slot 3; 3 by default. */
    HOTBAR_3("Hotbar 3", KeySignal.HOTBAR_3),
    /** Select hotbar slot 4; 4 by default. */
    HOTBAR_4("Hotbar 4", KeySignal.HOTBAR_4),
    /** Select hotbar slot 5; 5 by default. */
    HOTBAR_5("Hotbar 5", KeySignal.HOTBAR_5),
    /** Select hotbar slot 6; 6 by default. */
    HOTBAR_6("Hotbar 6", KeySignal.HOTBAR_6),
    /** Select hotbar slot 7; 7 by default. */
    HOTBAR_7("Hotbar 7", KeySignal.HOTBAR_7),
    /** Select hotbar slot 8; 8 by default. */
    HOTBAR_8("Hotbar 8", KeySignal.HOTBAR_8),
    /** Select hotbar slot 9; 9 by default. */
    HOTBAR_9("Hotbar 9", KeySignal.HOTBAR_9),
    /** Switch game mode; O by default. */
    SWITCH_GAME_MODE("Switch game mode", KeySignal.GAME_MODE_SWAP),
    /**
     * Walk; Alt by default. Alone: a short tap of the walk key while standing still. With other inputs: held.
     */
    WALK("Walk", KeySignal.WALKING),
    /** Crouch, held; Ctrl by default. */
    CROUCH("Crouch", KeySignal.CROUCHING),
    /** Sprint, held; Shift by default. */
    SPRINT("Sprint", KeySignal.SPRINTING),
    /** Jump; Space by default. */
    JUMP("Jump", KeySignal.JUMPING),
    /** Moving horizontally in any direction. */
    MOVE("Move", KeySignal.MOVING),
    /** Not moving at all. Cannot be combined with {@link #MOVE}. */
    STAND_STILL("Stand still", KeySignal.STANDING_STILL),
    /** Walk forward. Not reported to servers; use {@link #MOVE} for any direction. */
    WALK_FORWARD("Walk forward", null),
    /** Walk backward. Not reported to servers; use {@link #MOVE} for any direction. */
    WALK_BACKWARD("Walk backward", null),
    /** Strafe left. Not reported to servers; use {@link #MOVE} for any direction. */
    STRAFE_LEFT("Strafe left", null),
    /** Strafe right. Not reported to servers; use {@link #MOVE} for any direction. */
    STRAFE_RIGHT("Strafe right", null),
    /** Open the inventory. Not reported to servers. */
    OPEN_INVENTORY("Open inventory", null),
    /** Open the map. Not reported to servers. */
    OPEN_MAP("Open map", null),
    /** Open the chat. Not reported to servers. */
    OPEN_CHAT("Open chat", null),
    /** Drop the held item. Not reported to servers. */
    DROP_ITEM("Drop item", null),
    /** Switch the camera view. Not reported to servers. */
    SWITCH_CAMERA("Switch camera", null);

    private final String label;
    private final KeySignal signal;

    GameAction(String label, KeySignal signal) {
        this.label = label;
        this.signal = signal;
    }

    /**
     * Returns the action's name as shown to players.
     *
     * @return the label, for example {@code Walk forward}
     */
    @Override
    public String label() {
        return label;
    }

    /**
     * Returns how the server notices this action during play. {@link #WALK} alone gives {@link KeySignal#WALK_TAP};
     * with other inputs it gives {@link KeySignal#WALKING}.
     *
     * @param alone whether this action is the whole combination
     * @return the signal, or empty if the client never reports this action
     */
    @Override
    public Optional<KeySignal> signal(boolean alone) {
        if (this == WALK && alone) {
            return Optional.of(KeySignal.WALK_TAP);
        }
        return Optional.ofNullable(signal);
    }
}
