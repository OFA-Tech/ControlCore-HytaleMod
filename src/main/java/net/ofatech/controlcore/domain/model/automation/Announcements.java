package net.ofatech.controlcore.domain.model.automation;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

public record Announcements(
        boolean enabled, int intervalMinutes, Order order, Channel channel, int minPlayers, List<Item> items) {

    public static final Announcements DEFAULT = new Announcements(false, 10, Order.IN_TURN, Channel.CHAT, 0, List.of());

    public enum Order {
        IN_TURN,
        RANDOM;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Order> of(String text) {
            return Arrays.stream(values())
                    .filter(value -> value.id().equalsIgnoreCase(clean(text)))
                    .findFirst();
        }
    }

    public enum Channel {
        CHAT,
        TOAST,
        WARNING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Channel> of(String text) {
            return Arrays.stream(values())
                    .filter(value -> value.id().equalsIgnoreCase(clean(text)))
                    .findFirst();
        }
    }

    public record Item(String id, String text, boolean enabled) {

        public Item {
            Guard.notBlank(id, "id");
            text = text == null ? "" : text;
        }
    }

    public Announcements {
        intervalMinutes = Math.max(1, intervalMinutes);
        order = order == null ? Order.IN_TURN : order;
        channel = channel == null ? Channel.CHAT : channel;
        minPlayers = Math.max(0, minPlayers);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public Announcements withItems(List<Item> changed) {
        return new Announcements(enabled, intervalMinutes, order, channel, minPlayers, changed);
    }

    public Announcements withSettings(boolean on, int interval, Order newOrder, Channel newChannel, int min) {
        return new Announcements(on, interval, newOrder, newChannel, min, items);
    }

    public Optional<Item> find(String id) {
        return items.stream()
                .filter(item -> item.id().equalsIgnoreCase(clean(id)))
                .findFirst();
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip();
    }
}
