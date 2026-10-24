package net.ofatech.controlcore.application.history;

import java.util.function.Supplier;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Source;
import net.ofatech.controlcore.domain.ports.command.CommandSender;

public final class ChangeContext {

    public record Attribution(Actor actor, Source source, long undoes) {}

    private static final Attribution UNKNOWN = new Attribution(Actor.MOD, Source.API, 0);
    private static final ThreadLocal<Attribution> CURRENT = new ThreadLocal<>();

    private ChangeContext() {}

    public static Attribution current() {
        Attribution attribution = CURRENT.get();
        return attribution == null ? UNKNOWN : attribution;
    }

    public static <T> T fromPanel(PlayerId player, String name, Supplier<T> work) {
        return run(new Attribution(Actor.player(player, name), Source.PANEL, 0), work);
    }

    public static void fromPanel(PlayerId player, String name, Runnable work) {
        fromPanel(player, name, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T fromCommand(CommandSender sender, Supplier<T> work) {
        Actor actor = sender.player()
                .map(player -> Actor.player(player.id(), player.name()))
                .orElseGet(() -> Actor.console(sender.name()));
        return run(new Attribution(actor, Source.COMMAND, 0), work);
    }

    static <T> T rollingBack(Actor actor, long undoes, Supplier<T> work) {
        return run(new Attribution(actor, Source.ROLLBACK, undoes), work);
    }

    private static <T> T run(Attribution attribution, Supplier<T> work) {
        Attribution outer = CURRENT.get();
        if (outer != null && outer.source() == Source.ROLLBACK) {
            return work.get();
        }
        CURRENT.set(attribution);
        try {
            return work.get();
        } finally {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }
}
