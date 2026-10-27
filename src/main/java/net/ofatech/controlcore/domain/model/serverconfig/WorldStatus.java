package net.ofatech.controlcore.domain.model.serverconfig;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.text.Translator;

public record WorldStatus(int players, int chunks, int tps, String state) {

    private static final String PREFIX = "world-status:";

    public static String encode(int players, int chunks, int tps, String state) {
        return PREFIX + players + "/" + chunks + "/" + tps + "/" + state;
    }

    public String describe(Translator tr) {
        return tr.t("controlcore.world.status." + state, "players", players, "chunks", chunks, "tps", tps);
    }

    public static Optional<WorldStatus> decode(String text) {
        if (text == null || !text.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = text.substring(PREFIX.length()).split("/", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new WorldStatus(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
