package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

final class OverlaySize {

    private OverlaySize() {}

    static final int TOAST_CHARS_PER_LINE = 54;
    static final int WARNING_CHARS_PER_LINE = 70;

    static int toastHeight(String title, String text) {
        int titleHeight = title.isBlank() ? 0 : 19;
        return 18 + titleHeight + 17 * lines(text, TOAST_CHARS_PER_LINE);
    }

    static int warningHeight(String text) {
        return Math.max(34, 14 + 20 * lines(text, WARNING_CHARS_PER_LINE));
    }

    static int lines(String text, int width) {
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            int used = 0;
            lines++;
            for (String word : paragraph.strip().split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                int needed = used == 0 ? word.length() : used + 1 + word.length();
                if (needed > width && used > 0) {
                    lines++;
                    used = word.length();
                } else {
                    used = needed;
                }
                while (used > width) {
                    lines++;
                    used -= width;
                }
            }
        }
        return Math.max(1, lines);
    }
}
