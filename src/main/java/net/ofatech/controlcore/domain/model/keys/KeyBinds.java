package net.ofatech.controlcore.domain.model.keys;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The key combinations bound to one action; any of them runs it. Empty means unbound.
 *
 * @param combos the combinations, the main one first
 */
public record KeyBinds(List<KeyCombo> combos) {

    /** Written form of an unbound action. */
    public static final String NONE = "none";

    public static final KeyBinds UNBOUND = new KeyBinds(List.of());

    public KeyBinds {
        combos = combos == null ? List.of() : List.copyOf(combos);
    }

    /**
     * Parses text such as {@code ctrl+f}, {@code alt+3 | mouse_middle} or {@code none}.
     *
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

    public boolean isEmpty() {
        return combos.isEmpty();
    }

    /** @return the binds as players write them, for example {@code ALT + 3 | MOUSE_MIDDLE}, or {@code none} */
    @Override
    public String toString() {
        return combos.isEmpty() ? NONE : combos.stream().map(KeyCombo::toString).collect(Collectors.joining(" | "));
    }
}
