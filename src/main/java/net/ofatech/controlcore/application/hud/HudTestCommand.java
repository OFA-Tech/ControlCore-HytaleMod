package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import java.util.List;
import net.ofatech.controlcore.application.component.ModCommand;
import net.ofatech.controlcore.application.keymaps.InputProbeCommand;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.Overlay;
import net.ofatech.controlcore.domain.model.hud.ToastLevel;
import net.ofatech.controlcore.domain.ports.command.ArgType;
import net.ofatech.controlcore.domain.ports.command.CommandContext;
import net.ofatech.controlcore.domain.ports.command.CommandSpec;
import net.ofatech.controlcore.domain.ports.command.Reply;
import net.ofatech.controlcore.domain.ports.game.OverlayDisplay;
import net.ofatech.controlcore.domain.result.Result;

@AutoService(ModCommand.class)
public final class HudTestCommand implements ModCommand {

    private static final List<String> CORNERS =
            List.of(Overlay.TOP_RIGHT, Overlay.TOP_LEFT, Overlay.BOTTOM_RIGHT, Overlay.BOTTOM_LEFT);
    private static final List<String> PLACES = List.of(Overlay.UNDER_COMPASS, Overlay.TOP, Overlay.BOTTOM);

    private final OverlayDisplay display;

    public HudTestCommand(OverlayDisplay display) {
        this.display = display;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("cc-hud-test")
                .description("Test the ControlCore overlay: 0-3 toast corner and warning place, hytale, or off")
                .permission(InputProbeCommand.PERMISSION)
                .arg("step", ArgType.STRING, "0, 1, 2, 3, hytale or off")
                .requirePlayer();
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo sender = context.sender().player().orElseThrow();
        String step = context.string("step").strip().toLowerCase(java.util.Locale.ROOT);
        if (step.equals("off")) {
            display.render(sender.id(), new Overlay(List.of(), Overlay.TOP_RIGHT, List.of(), Overlay.UNDER_COMPASS));
            return Result.success(Reply.of("Overlay cleared."));
        }
        if (step.equals("hytale")) {
            for (ToastLevel level : ToastLevel.values()) {
                display.hytaleToast(sender.id(), "ControlCore", "Hytale notification: " + level.id(), level);
            }
            return Result.success(Reply.of("Sent 4 Hytale notifications."));
        }
        int index = step.matches("[0-3]") ? Integer.parseInt(step) : 0;
        String corner = CORNERS.get(index);
        String place = PLACES.get(Math.min(index, PLACES.size() - 1));
        List<Overlay.Line> toasts = List.of(
                new Overlay.Line("Saved", "Welcome message = Hello!", ToastLevel.SUCCESS),
                new Overlay.Line("", "Backup done in 4 s (" + corner + ")", ToastLevel.INFO),
                new Overlay.Line("Rule failed", "Vote reminder: command not found", ToastLevel.ERROR));
        List<Overlay.Line> warnings = List.of(
                new Overlay.Line("", "Server restarts in 10 minutes (" + place + ")", ToastLevel.WARNING),
                new Overlay.Line("", "PvP is ON in this world", ToastLevel.ERROR));
        display.render(sender.id(), new Overlay(toasts, corner, warnings, place));
        return Result.success(Reply.of("Toasts: " + corner + ", warnings: " + place + "."));
    }
}
