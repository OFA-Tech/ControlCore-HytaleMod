package net.ofatech.controlcore.application.hud;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.ports.game.PlayerContext;

record ContextRule(HudPart part, boolean show, String when) {

    static Optional<ContextRule> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String[] words = line.strip().toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length != 3 || !(words[1].equals("hide") || words[1].equals("show")) || !valid(words[2])) {
            return Optional.empty();
        }
        String slug = words[0].replace('_', '-');
        return Arrays.stream(HudPart.values())
                .filter(part -> part.slug().equals(slug))
                .findFirst()
                .map(part -> new ContextRule(part, words[1].equals("show"), words[2]));
    }

    private static boolean valid(String when) {
        return when.equals("combat")
                || when.equals("no-combat")
                || (when.startsWith("world:") && when.length() > 6)
                || (when.startsWith("mode:") && when.length() > 5);
    }

    boolean matches(PlayerContext.Context context) {
        if (when.equals("combat")) {
            return context.inCombat();
        }
        if (when.equals("no-combat")) {
            return !context.inCombat();
        }
        if (when.startsWith("world:")) {
            return when.substring(6).equalsIgnoreCase(context.world());
        }
        return when.substring(5).equalsIgnoreCase(context.gameMode());
    }
}
