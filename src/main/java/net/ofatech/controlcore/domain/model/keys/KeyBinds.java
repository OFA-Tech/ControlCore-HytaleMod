package net.ofatech.controlcore.domain.model.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The key combinations bound to one action; any of them runs it. Empty means unbound.
 *
 * @param combos the combinations, the main one first; {@code null} is treated as empty. Stored as an immutable copy
 */
public record KeyBinds(List<KeyCombo> combos) {

    /** Written form of an unbound action: {@code none}. */
    public static final String NONE = "none";

    /** Binds with no combinations, for an unbound action. */
    public static final KeyBinds UNBOUND = new KeyBinds(List.of());

    /**
     * Creates binds from a list of combinations.
     *
     * @param combos the combinations, the main one first; {@code null} means unbound
     */
    public KeyBinds {
        combos = combos == null ? List.of() : List.copyOf(combos);
    }

    /**
     * Parses text such as {@code ctrl+f}, {@code alt+3 | mouse_middle} or {@code none}. Combinations are separated
     * by {@code |} and parsed with {@link KeyCombo#parse(String)}; duplicates are dropped. Null, blank or
     * {@code none} (any case) gives {@link #UNBOUND}.
     *
     * @param text the written binds
     * @return the binds, or the error {@code Keys.InvalidCombo}
     */
    public static Result<KeyBinds> parse(String text) {
        if (text == null || text.isBlank() || text.strip().equalsIgnoreCase(NONE)) {
            return Result.success(UNBOUND);
        }
        List<KeyCombo> combos = new ArrayList<>();
        for (String part : text.split("\\|")) {
            Result<KeyCombo> combo = KeyCombo.parse(part);
            if (combo.isFailure()) {
                return Result.failure(combo.error());
            }
            if (!combos.contains(combo.value())) {
                combos.add(combo.value());
            }
        }
        return Result.success(new KeyBinds(combos));
    }

    /**
     * Tells whether the action is unbound.
     *
     * @return {@code true} if there are no combinations
     */
    public boolean isEmpty() {
        return combos.isEmpty();
    }

    /**
     * Returns the binds as players write them; {@link #parse(String)} reads this form back.
     *
     * @return the binds, for example {@code ALT + 3 | MOUSE_MIDDLE}, or {@code none} if unbound
     */
    @Override
    public String toString() {
        return combos.isEmpty() ? NONE : combos.stream().map(KeyCombo::toString).collect(Collectors.joining(" | "));
    }
}
