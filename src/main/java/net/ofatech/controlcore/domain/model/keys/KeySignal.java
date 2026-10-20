package net.ofatech.controlcore.domain.model.keys;

/**
 * Something the game client tells the server while a player plays, from which ControlCore can tell that a key was
 * used. The server never sees raw keys; only these signals.
 */
public enum KeySignal {
    /** Ability 1 (Q by default); only sent if the held item has an ability 1. */
    ABILITY_1(true),
    /** Ability 2 (E by default); only sent if the held item has an ability 2. */
    ABILITY_2(true),
    /** Ability 3 (R by default). */
    ABILITY_3(true),
    /** Use / interact (F by default). */
    USE(true),
    /** Primary action (left mouse button). */
    PRIMARY(true),
    /** Secondary action (right mouse button); only sent if the held item has one. */
    SECONDARY(true),
    /** Pick block (middle mouse button). */
    PICK(true),
    HOTBAR_1(true),
    HOTBAR_2(true),
    HOTBAR_3(true),
    HOTBAR_4(true),
    HOTBAR_5(true),
    HOTBAR_6(true),
    HOTBAR_7(true),
    HOTBAR_8(true),
    HOTBAR_9(true),
    /** Switch game mode (O by default). */
    GAME_MODE_SWAP(true),
    /** A short tap of the walk key (Alt by default) while standing still. */
    WALK_TAP(true),
    /** Crouching (Ctrl by default) is held. */
    CROUCHING(false),
    /** Walking (Alt by default) is held. */
    WALKING(false),
    /** Sprinting (Shift by default). */
    SPRINTING(false),
    /** Jumping (Space by default). */
    JUMPING(false),
    /** The player is moving horizontally. */
    MOVING(false),
    /** The player is not moving at all. */
    STANDING_STILL(false);

    private final boolean press;

    KeySignal(boolean press) {
        this.press = press;
    }

    /** @return whether this is a single press; otherwise it is a state that stays on while a key is held */
    public boolean isPress() {
        return press;
    }

    /** @return whether this is a hotbar slot change */
    public boolean isHotbar() {
        return ordinal() >= HOTBAR_1.ordinal() && ordinal() <= HOTBAR_9.ordinal();
    }

    /**
     * @param slot hotbar slot, 1 to 9
     * @return the signal of that slot
     */
    public static KeySignal hotbar(int slot) {
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("Hotbar slot must be 1 to 9: " + slot);
        }
        return values()[HOTBAR_1.ordinal() + slot - 1];
    }
}
