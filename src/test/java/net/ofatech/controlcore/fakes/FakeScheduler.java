package net.ofatech.controlcore.fakes;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

/** Records scheduled work; tests run it with {@link #runAll()}. */
public final class FakeScheduler implements Scheduler {

    public static final class Entry {
        public final Duration delay;
        public final Duration period;
        public final Runnable work;
        public boolean cancelled;

        Entry(Duration delay, Duration period, Runnable work) {
            this.delay = delay;
            this.period = period;
            this.work = work;
        }
    }

    public final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public ScheduledTask schedule(Duration initialDelay, Duration period, Runnable work) {
        return add(new Entry(initialDelay, period, work));
    }

    @Override
    public ScheduledTask runLater(Duration delay, Runnable work) {
        return add(new Entry(delay, null, work));
    }

    public void runAll() {
        entries.stream().filter(e -> !e.cancelled).forEach(e -> e.work.run());
    }

    private ScheduledTask add(Entry entry) {
        entries.add(entry);
        return () -> entry.cancelled = true;
    }
}
