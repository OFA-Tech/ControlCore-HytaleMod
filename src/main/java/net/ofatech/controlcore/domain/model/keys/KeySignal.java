package net.ofatech.controlcore.domain.model.keys;

/**
 * Something the game client tells the server while a player plays, from which ControlCore can tell that a key was
 * used. The server never sees raw keys; only these signals. A signal is either a single press ({@link #isPress()})
 * or a state that stays on while a key is held.
 */
public enum KeySignal {
    /** Ability 1 (Q by default); sent if the held item has an ability 1, or with ControlCore's empty-hand keys. */
    ABILITY_1(true),
    /** Ability 2 (E by default); sent if the held item has an ability 2, or with ControlCore's empty-hand keys. */
    ABILITY_2(true),
    /** Ability 3 (R by default). */
    ABILITY_3(true),
    /** Ability 4; sent if the held item has one, or with ControlCore's empty-hand keys. */
    ABILITY_4(true),
    /** Dodge; sent if the held item has a dodge interaction, or with ControlCore's empty-hand keys. */
    DODGE(true),
    /** Use / interact (F by default). */
    USE(true),
    /** Primary action (left mouse button). */
    PRIMARY(true),
    /**
     * Secondary action (right mouse button); sent if the held item has one, or with an empty hand (empty-hand
     * keys).
     */
    SECONDARY(true),
    /** Pick block (middle mouse button). */
    PICK(true),
    /** Hotbar slot 1 selected (1 by default). */
    HOTBAR_1(true),
    /** Hotbar slot 2 selected (2 by default). */
    HOTBAR_2(true),
    /** Hotbar slot 3 selected (3 by default). */
    HOTBAR_3(true),
    /** Hotbar slot 4 selected (4 by default). */
    HOTBAR_4(true),
    /** Hotbar slot 5 selected (5 by default). */
    HOTBAR_5(true),
    /** Hotbar slot 6 selected (6 by default). */
    HOTBAR_6(true),
    /** Hotbar slot 7 selected (7 by default). */
    HOTBAR_7(true),
    /** Hotbar slot 8 selected (8 by default). */
    HOTBAR_8(true),
    /** Hotbar slot 9 selected (9 by default). */
    HOTBAR_9(true),
    /** Switch game mode (O by default). */
    GAME_MODE_SWAP(true),
    /**
     * A short tap of the walk key (Alt by default) while standing still: released within half a second, with no
     * movement or other press meanwhile.
     */
    WALK_TAP(true),
    /** Crouching (Ctrl by default) is held. */
    CROUCHING(false),
    /** Walking (Alt by default) is held. */
    WALKING(false),
    /** Sprinting (Shift by default) is on. */
    SPRINTING(false),
    /** Jumping (Space by default) is on. */
    JUMPING(false),
    /** The player is moving horizontally. */
    MOVING(false),
    /** The player is not moving at all. */
    STANDING_STILL(false);

    private final boolean press;

    KeySignal(boolean press) {
        this.press = press;
    }

    /**
     * Tells whether this signal is a single press rather than a held state.
     *
     * @return {@code true} for a single press; {@code false} for a state that stays on while a key is held
     */
    public boolean isPress() {
        return press;
    }

    /**
     * Tells whether this signal is a hotbar slot change, {@link #HOTBAR_1} to {@link #HOTBAR_9}.
     *
     * @return {@code true} for a hotbar slot change
     */
    public boolean isHotbar() {
        return ordinal() >= HOTBAR_1.ordinal() && ordinal() <= HOTBAR_9.ordinal();
    }

    /**
     * Returns the signal for selecting a hotbar slot.
     *
     * @param slot hotbar slot, 1 to 9
     * @return the signal of that slot, for example {@link #HOTBAR_3} for {@code 3}
     * @throws IllegalArgumentException if {@code slot} is not between 1 and 9
     */
    public static KeySignal hotbar(int slot) {
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("Hotbar slot must be 1 to 9: " + slot);
        }
        return values()[HOTBAR_1.ordinal() + slot - 1];
    }
}
