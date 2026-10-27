package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.application.automation.AutomationService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.ports.game.PlayerContext;
import net.ofatech.controlcore.domain.ports.game.Players;
import net.ofatech.controlcore.domain.ports.game.Scheduler;

@AutoService(FeatureModule.class)
public final class SituationWatch implements FeatureModule {

    static final Duration EVERY = Duration.ofSeconds(1);

    private final PlayerContext context;
    private final HudService hud;
    private final AutomationService automation;
    private final Players players;
    private final Scheduler scheduler;
    private final Map<PlayerId, PlayerContext.Context> last = new ConcurrentHashMap<>();
    private Scheduler.ScheduledTask task;

    public SituationWatch(
            PlayerContext context, HudService hud, AutomationService automation, Players players, Scheduler scheduler) {
        this.context = context;
        this.hud = hud;
        this.automation = automation;
        this.players = players;
        this.scheduler = scheduler;
    }

    @Override
    public void setup(ModuleContext moduleContext) {}

    @Override
    public void start(ModuleContext moduleContext) {
        task = scheduler.schedule(EVERY, EVERY, this::check);
    }

    void check() {
        Map<PlayerId, PlayerContext.Context> seen = new ConcurrentHashMap<>();
        for (PlayerInfo player : players.online()) {
            PlayerContext.Context now = context.current(player.id());
            seen.put(player.id(), now);
            PlayerContext.Context before = last.get(player.id());
            if (before == null || before.equals(now)) {
                continue;
            }
            if (before.inCombat() != now.inCombat()) {
                automation.onCombat(player, now.inCombat());
            }
            if (hud.hasSituationRules()) {
                hud.contextChanged(player.id());
            }
        }
        last.clear();
        last.putAll(seen);
    }

    @Override
    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }
}
