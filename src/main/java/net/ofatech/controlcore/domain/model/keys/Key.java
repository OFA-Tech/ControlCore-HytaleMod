package net.ofatech.controlcore.domain.model.keys;

import java.util.List;
import java.util.Optional;

/**
 * A physical key or mouse button, named after its default role in Hytale. Every key can be used in a binding;
 * {@link #signal} tells whether the server can notice it during play. The server cannot see which key a player
 * rebound in their own client settings, so {@code Q} really means "ability 1". Use {@link GameAction} when the
 * action matters more than the key.
 */
public enum Key implements KeyInput {
    /** Letter A; not reported to servers. */
    A,
    /** Letter B; not reported to servers. */
    B,
    /** Letter C; not reported to servers. */
    C,
    /** Letter D; not reported to servers. */
    D,
    /** Letter E; noticed as {@link KeySignal#ABILITY_2}, ability 2 by default. */
    E(KeySignal.ABILITY_2),
    /** Letter F; noticed as {@link KeySignal#USE}, use / interact by default. */
    F(KeySignal.USE),
    /** Letter G; not reported to servers. */
    G,
    /** Letter H; not reported to servers. */
    H,
    /** Letter I; not reported to servers. */
    I,
    /** Letter J; not reported to servers. */
    J,
    /** Letter K; not reported to servers. */
    K,
    /** Letter L; not reported to servers. */
    L,
    /** Letter M; not reported to servers. */
    M,
    /** Letter N; not reported to servers. */
    N,
    /** Letter O; noticed as {@link KeySignal#GAME_MODE_SWAP}, switch game mode by default. */
    O(KeySignal.GAME_MODE_SWAP),
    /** Letter P; not reported to servers. */
    P,
    /** Letter Q; noticed as {@link KeySignal#ABILITY_1}, ability 1 by default. */
    Q(KeySignal.ABILITY_1),
    /** Letter R; noticed as {@link KeySignal#ABILITY_3}, ability 3 by default. */
    R(KeySignal.ABILITY_3),
    /** Letter S; not reported to servers. */
    S,
    /** Letter T; not reported to servers. */
    T,
    /** Letter U; not reported to servers. */
    U,
    /** Letter V; not reported to servers. */
    V,
    /** Letter W; not reported to servers. */
    W,
    /** Letter X; not reported to servers. */
    X,
    /** Letter Y; not reported to servers. */
    Y,
    /** Letter Z; not reported to servers. */
    Z,
    /** Digit key 0 (written {@code 0}); not reported to servers. */
    NUM_0("0"),
    /** Digit key 1 (written {@code 1}); noticed as {@link KeySignal#HOTBAR_1}, hotbar slot 1 by default. */
    NUM_1("1", KeySignal.HOTBAR_1),
    /** Digit key 2 (written {@code 2}); noticed as {@link KeySignal#HOTBAR_2}, hotbar slot 2 by default. */
    NUM_2("2", KeySignal.HOTBAR_2),
    /** Digit key 3 (written {@code 3}); noticed as {@link KeySignal#HOTBAR_3}, hotbar slot 3 by default. */
    NUM_3("3", KeySignal.HOTBAR_3),
    /** Digit key 4 (written {@code 4}); noticed as {@link KeySignal#HOTBAR_4}, hotbar slot 4 by default. */
    NUM_4("4", KeySignal.HOTBAR_4),
    /** Digit key 5 (written {@code 5}); noticed as {@link KeySignal#HOTBAR_5}, hotbar slot 5 by default. */
    NUM_5("5", KeySignal.HOTBAR_5),
    /** Digit key 6 (written {@code 6}); noticed as {@link KeySignal#HOTBAR_6}, hotbar slot 6 by default. */
    NUM_6("6", KeySignal.HOTBAR_6),
    /** Digit key 7 (written {@code 7}); noticed as {@link KeySignal#HOTBAR_7}, hotbar slot 7 by default. */
    NUM_7("7", KeySignal.HOTBAR_7),
    /** Digit key 8 (written {@code 8}); noticed as {@link KeySignal#HOTBAR_8}, hotbar slot 8 by default. */
    NUM_8("8", KeySignal.HOTBAR_8),
    /** Digit key 9 (written {@code 9}); noticed as {@link KeySignal#HOTBAR_9}, hotbar slot 9 by default. */
    NUM_9("9", KeySignal.HOTBAR_9),
    /** Function key F1; not reported to servers. */
    F1,
    /** Function key F2; not reported to servers. */
    F2,
    /** Function key F3; not reported to servers. */
    F3,
    /** Function key F4; not reported to servers. */
    F4,
    /** Function key F5; not reported to servers. */
    F5,
    /** Function key F6; not reported to servers. */
    F6,
    /** Function key F7; not reported to servers. */
    F7,
    /** Function key F8; not reported to servers. */
    F8,
    /** Function key F9; not reported to servers. */
    F9,
    /** Function key F10; not reported to servers. */
    F10,
    /** Function key F11; not reported to servers. */
    F11,
    /** Function key F12; not reported to servers. */
    F12,
    /** Escape key (shown as {@code ESC}); not reported to servers. */
    ESCAPE("ESC"),
    /** Tab key; not reported to servers. */
    TAB,
    /** Caps Lock key (shown as {@code CAPS LOCK}); not reported to servers. */
    CAPS("CAPS LOCK"),
    /** Shift key; noticed as {@link KeySignal#SPRINTING} held, sprint by default. */
    SHIFT(KeySignal.SPRINTING),
    /** Ctrl key; noticed as {@link KeySignal#CROUCHING} held, crouch by default. */
    CTRL(KeySignal.CROUCHING),
    /**
     * Alt key, walk by default. Alone: a short tap while standing still ({@link KeySignal#WALK_TAP}). With other
     * keys: held ({@link KeySignal#WALKING}).
     */
    ALT(KeySignal.WALKING),
    /** Space bar; noticed as {@link KeySignal#JUMPING}, jump by default. */
    SPACE(KeySignal.JUMPING),
    /** Enter key; not reported to servers. */
    ENTER,
    /** Backspace key; not reported to servers. */
    BACKSPACE,
    /** Delete key; not reported to servers. */
    DELETE,
    /** Insert key; not reported to servers. */
    INSERT,
    /** Home key; not reported to servers. */
    HOME,
    /** End key; not reported to servers. */
    END,
    /** Page Up key (shown as {@code PAGE UP}); not reported to servers. */
    PAGE_UP("PAGE UP"),
    /** Page Down key (shown as {@code PAGE DOWN}); not reported to servers. */
    PAGE_DOWN("PAGE DOWN"),
    /** Up arrow key; not reported to servers. */
    UP,
    /** Down arrow key; not reported to servers. */
    DOWN,
    /** Left arrow key; not reported to servers. */
    LEFT,
    /** Right arrow key; not reported to servers. */
    RIGHT,
    /** Grave accent / backtick key ({@code `}); not reported to servers. */
    GRAVE("`"),
    /** Minus key ({@code -}); not reported to servers. */
    MINUS("-"),
    /** Equals key ({@code =}); not reported to servers. */
    EQUALS("="),
    /** Left bracket key ({@code [}); not reported to servers. */
    LEFT_BRACKET("["),
    /** Right bracket key ({@code ]}); not reported to servers. */
    RIGHT_BRACKET("]"),
    /** Backslash key ({@code \}); not reported to servers. */
    BACKSLASH("\\"),
    /** Semicolon key ({@code ;}); not reported to servers. */
    SEMICOLON(";"),
    /** Apostrophe key ({@code '}); not reported to servers. */
    APOSTROPHE("'"),
    /** Comma key ({@code ,}); not reported to servers. */
    COMMA(","),
    /** Period key ({@code .}); not reported to servers. */
    PERIOD("."),
    /** Slash key ({@code /}); not reported to servers. */
    SLASH("/"),
    /** Numpad 0 (written {@code NUMPAD 0}); not reported to servers. */
    NUMPAD_0("NUMPAD 0"),
    /** Numpad 1 (written {@code NUMPAD 1}); not reported to servers. */
    NUMPAD_1("NUMPAD 1"),
    /** Numpad 2 (written {@code NUMPAD 2}); not reported to servers. */
    NUMPAD_2("NUMPAD 2"),
    /** Numpad 3 (written {@code NUMPAD 3}); not reported to servers. */
    NUMPAD_3("NUMPAD 3"),
    /** Numpad 4 (written {@code NUMPAD 4}); not reported to servers. */
    NUMPAD_4("NUMPAD 4"),
    /** Numpad 5 (written {@code NUMPAD 5}); not reported to servers. */
    NUMPAD_5("NUMPAD 5"),
    /** Numpad 6 (written {@code NUMPAD 6}); not reported to servers. */
    NUMPAD_6("NUMPAD 6"),
    /** Numpad 7 (written {@code NUMPAD 7}); not reported to servers. */
    NUMPAD_7("NUMPAD 7"),
    /** Numpad 8 (written {@code NUMPAD 8}); not reported to servers. */
    NUMPAD_8("NUMPAD 8"),
    /** Numpad 9 (written {@code NUMPAD 9}); not reported to servers. */
    NUMPAD_9("NUMPAD 9"),
    /** Numpad / key (written {@code NUMPAD DIVIDE}); not reported to servers. */
    NUMPAD_DIVIDE("NUMPAD /"),
    /** Numpad * key (written {@code NUMPAD MULTIPLY}); not reported to servers. */
    NUMPAD_MULTIPLY("NUMPAD *"),
    /** Numpad - key (written {@code NUMPAD MINUS}); not reported to servers. */
    NUMPAD_MINUS("NUMPAD -"),
    /** Numpad + key (written {@code NUMPAD PLUS}); not reported to servers. */
    NUMPAD_PLUS("NUMPAD +"),
    /** Numpad ENTER key (written {@code NUMPAD ENTER}); not reported to servers. */
    NUMPAD_ENTER("NUMPAD ENTER"),
    /** Numpad . key (written {@code NUMPAD PERIOD}); not reported to servers. */
    NUMPAD_PERIOD("NUMPAD ."),
    /** Num Lock key; not reported to servers. */
    NUM_LOCK("NUM LOCK"),
    /** Print Screen key; not reported to servers. */
    PRINT_SCREEN("PRINT SCREEN"),
    /** Scroll Lock key; not reported to servers. */
    SCROLL_LOCK("SCROLL LOCK"),
    /** Pause key; not reported to servers. */
    PAUSE("PAUSE"),
    /** Menu (application) key; not reported to servers. */
    MENU("MENU"),
    /** Left mouse button; noticed as {@link KeySignal#PRIMARY}, the primary action by default. */
    MOUSE_LEFT("MOUSE LEFT", KeySignal.PRIMARY),
    /** Right mouse button; noticed as {@link KeySignal#SECONDARY}, the secondary action by default. */
    MOUSE_RIGHT("MOUSE RIGHT", KeySignal.SECONDARY),
    /** Middle mouse button; noticed as {@link KeySignal#PICK}, pick block by default. */
    MOUSE_MIDDLE("MOUSE MIDDLE", KeySignal.PICK),
    /** Fourth (side) mouse button; not reported to servers. */
    MOUSE_4("MOUSE 4"),
    /** Fifth (side) mouse button; not reported to servers. */
    MOUSE_5("MOUSE 5");

    /** The keys that can be held while another key is pressed, in the order they are written: Ctrl, Alt, Shift. */
    public static final List<Key> HOLDS = List.of(CTRL, ALT, SHIFT);

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

    /**
     * Returns the key's name as shown to players.
     *
     * @return the label, for example {@code CTRL}, {@code 3} or {@code MOUSE LEFT}
     */
    @Override
    public String label() {
        return label != null ? label : name();
    }

    /**
     * Returns how the server notices this key during play. {@link #ALT} alone gives {@link KeySignal#WALK_TAP};
     * with other keys it gives {@link KeySignal#WALKING}.
     *
     * @param alone whether this key is the whole combination
     * @return the signal, or empty if the client never reports this key
     */
    @Override
    public Optional<KeySignal> signal(boolean alone) {
        if (this == ALT && alone) {
            return Optional.of(KeySignal.WALK_TAP);
        }
        return Optional.ofNullable(signal);
    }
}
