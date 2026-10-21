package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;

/**
 * A game action, whatever key the player has it on. Using actions instead of {@link Key}s keeps a binding working
 * when a player rebinds keys in their client settings.
 */
public enum GameAction implements KeyInput {
    ATTACK("Attack", KeySignal.PRIMARY),
    SECONDARY_ACTION("Secondary action", KeySignal.SECONDARY),
    ABILITY_1("Ability 1", KeySignal.ABILITY_1),
    ABILITY_2("Ability 2", KeySignal.ABILITY_2),
    ABILITY_3("Ability 3", KeySignal.ABILITY_3),
    ABILITY_4("Ability 4", KeySignal.ABILITY_4),
    DODGE("Dodge", KeySignal.DODGE),
    INTERACT("Interact", KeySignal.USE),
    PICK_BLOCK("Pick block", KeySignal.PICK),
    HOTBAR_1("Hotbar 1", KeySignal.HOTBAR_1),
    HOTBAR_2("Hotbar 2", KeySignal.HOTBAR_2),
    HOTBAR_3("Hotbar 3", KeySignal.HOTBAR_3),
    HOTBAR_4("Hotbar 4", KeySignal.HOTBAR_4),
    HOTBAR_5("Hotbar 5", KeySignal.HOTBAR_5),
    HOTBAR_6("Hotbar 6", KeySignal.HOTBAR_6),
    HOTBAR_7("Hotbar 7", KeySignal.HOTBAR_7),
    HOTBAR_8("Hotbar 8", KeySignal.HOTBAR_8),
    HOTBAR_9("Hotbar 9", KeySignal.HOTBAR_9),
    SWITCH_GAME_MODE("Switch game mode", KeySignal.GAME_MODE_SWAP),
    /** Alone: a short tap of the walk key while standing still. With other inputs: held. */
    WALK("Walk", KeySignal.WALKING),
    CROUCH("Crouch", KeySignal.CROUCHING),
    SPRINT("Sprint", KeySignal.SPRINTING),
    JUMP("Jump", KeySignal.JUMPING),
    /** Moving in any direction. */
    MOVE("Move", KeySignal.MOVING),
    STAND_STILL("Stand still", KeySignal.STANDING_STILL),
    WALK_FORWARD("Walk forward", null),
    WALK_BACKWARD("Walk backward", null),
    STRAFE_LEFT("Strafe left", null),
    STRAFE_RIGHT("Strafe right", null),
    OPEN_INVENTORY("Open inventory", null),
    OPEN_MAP("Open map", null),
    OPEN_CHAT("Open chat", null),
    DROP_ITEM("Drop item", null),
    SWITCH_CAMERA("Switch camera", null);

    private final String label;
    private final KeySignal signal;

    GameAction(String label, KeySignal signal) {
        this.label = label;
        this.signal = signal;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Optional<KeySignal> signal(boolean alone) {
        if (this == WALK && alone) {
            return Optional.of(KeySignal.WALK_TAP);
        }
        return Optional.ofNullable(signal);
    }
}
