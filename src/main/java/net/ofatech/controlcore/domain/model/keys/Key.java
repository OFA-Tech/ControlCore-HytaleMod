package net.ofatech.controlcore.domain.model.keys;

import java.util.Optional;

/**
 * A physical key or mouse button, named after its default role in Hytale. Every key can be used in a binding;
 * {@link #signal} tells whether the server can notice it during play. The server cannot see which key a player
 * rebound in their own client settings, so {@code Q} really means "ability 1".
 */
public enum Key implements KeyInput {
    A,
    B,
    C,
    D,
    E(KeySignal.ABILITY_2),
    F(KeySignal.USE),
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O(KeySignal.GAME_MODE_SWAP),
    P,
    Q(KeySignal.ABILITY_1),
    R(KeySignal.ABILITY_3),
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    NUM_0("0"),
    NUM_1("1", KeySignal.HOTBAR_1),
    NUM_2("2", KeySignal.HOTBAR_2),
    NUM_3("3", KeySignal.HOTBAR_3),
    NUM_4("4", KeySignal.HOTBAR_4),
    NUM_5("5", KeySignal.HOTBAR_5),
    NUM_6("6", KeySignal.HOTBAR_6),
    NUM_7("7", KeySignal.HOTBAR_7),
    NUM_8("8", KeySignal.HOTBAR_8),
    NUM_9("9", KeySignal.HOTBAR_9),
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    ESCAPE("ESC"),
    TAB,
    CAPS("CAPS LOCK"),
    SHIFT(KeySignal.SPRINTING),
    CTRL(KeySignal.CROUCHING),
    /** Alone: a short tap while standing still. With other keys: held. */
    ALT(KeySignal.WALKING),
    SPACE(KeySignal.JUMPING),
    ENTER,
    BACKSPACE,
    DELETE,
    INSERT,
    HOME,
    END,
    PAGE_UP("PAGE UP"),
    PAGE_DOWN("PAGE DOWN"),
    UP,
    DOWN,
    LEFT,
    RIGHT,
    GRAVE("`"),
    MINUS("-"),
    EQUALS("="),
    LEFT_BRACKET("["),
    RIGHT_BRACKET("]"),
    BACKSLASH("\\"),
    SEMICOLON(";"),
    APOSTROPHE("'"),
    COMMA(","),
    PERIOD("."),
    SLASH("/"),
    MOUSE_LEFT("MOUSE LEFT", KeySignal.PRIMARY),
    MOUSE_RIGHT("MOUSE RIGHT", KeySignal.SECONDARY),
    MOUSE_MIDDLE("MOUSE MIDDLE", KeySignal.PICK),
    MOUSE_4("MOUSE 4"),
    MOUSE_5("MOUSE 5");

    private final String label;
    private final KeySignal signal;

    Key() {
        this(null, null);
    }

    Key(KeySignal signal) {
        this(null, signal);
    }

    Key(String label) {
        this(label, null);
    }

    Key(String label, KeySignal signal) {
        this.label = label;
        this.signal = signal;
    }

    @Override
    public String label() {
        return label != null ? label : name();
    }

    @Override
    public Optional<KeySignal> signal(boolean alone) {
        if (this == ALT && alone) {
            return Optional.of(KeySignal.WALK_TAP);
        }
        return Optional.ofNullable(signal);
    }
}
