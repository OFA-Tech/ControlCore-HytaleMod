package net.ofatech.controlcore.domain.model.hud;

import java.util.List;

public record Overlay(List<Line> toasts, String toastCorner, List<Line> warnings, String warningPlace) {

    public record Line(String title, String text, ToastLevel level) {}

    public static final String TOP_RIGHT = "top-right";
    public static final String TOP_LEFT = "top-left";
    public static final String BOTTOM_RIGHT = "bottom-right";
    public static final String HYTALE = "hytale";
    public static final String UNDER_COMPASS = "under-compass";
    public static final String BOTTOM = "bottom";

    public Overlay {
        toasts = List.copyOf(toasts);
        warnings = List.copyOf(warnings);
    }

    public boolean empty() {
        return toasts.isEmpty() && warnings.isEmpty();
    }
}
