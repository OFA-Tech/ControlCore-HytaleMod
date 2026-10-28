package net.ofatech.controlcore.application.players;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.system.Clock;

public final class RecentChat {

    static final int KEPT = 200;
    static final int SHOWN = 50;
    static final int PER_PLAYER = 20;
    static final int MAX_TEXT = 256;

    record Line(Instant at, PlayerId player, String name, String text) {}

    private final Deque<Line> lines = new ArrayDeque<>();
    private final Clock clock;

    public RecentChat(Clock clock) {
        this.clock = clock;
    }

    public synchronized void add(PlayerId player, String name, String text) {
        lines.addLast(new Line(clock.now(), player, oneLine(name, MAX_TEXT), oneLine(text, MAX_TEXT)));
        while (lines.size() > KEPT) {
            lines.pollFirst();
        }
    }

    synchronized List<Line> last(int count) {
        List<Line> all = List.copyOf(lines);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    synchronized List<Line> of(PlayerId player, int count) {
        List<Line> theirs =
                lines.stream().filter(line -> line.player().equals(player)).toList();
        return theirs.subList(Math.max(0, theirs.size() - count), theirs.size());
    }

    static String oneLine(String text, int max) {
        String flat = text == null ? "" : text.replaceAll("\\p{Cntrl}", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }
}
