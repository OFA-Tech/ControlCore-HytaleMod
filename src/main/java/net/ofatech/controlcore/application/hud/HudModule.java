package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.api.KeymapsApi;
import net.ofatech.controlcore.application.automation.NotificationService;
import net.ofatech.controlcore.application.component.FeatureModule;
import net.ofatech.controlcore.application.component.ModuleContext;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.text.Text;

@AutoService(FeatureModule.class)
public final class HudModule implements FeatureModule {

    static final String TOGGLE_HUD = "toggle-hud";

    private final HudService hud;
    private final KeymapsApi keymaps;
    private final NotificationService notifications;
    private Subscription changes;
    private Subscription notificationChanges;

    public HudModule(HudService hud, KeymapsApi keymaps, NotificationService notifications) {
        this.hud = hud;
        this.keymaps = keymaps;
        this.notifications = notifications;
    }

    @Override
    public void setup(ModuleContext context) {
        keymaps.register(KeyAction.of("controlcore", TOGGLE_HUD)
                .displayName(Text.key("controlcore.keys.actions.toggle_hud.name", "Hide or show the HUD"))
                .describedAs(Text.key(
                        "controlcore.keys.actions.toggle_hud.desc",
                        "Hides the whole HUD (the game's and mods'), for example for screenshots; press again to show"
                                + " it."))
                .onPress(player -> hud.setHidden(player, !hud.isHidden(player))));
    }

    @Override
    public void start(ModuleContext context) {
        changes = context.events().subscribe(SettingChanged.class, hud::onSettingChanged);
        notificationChanges = context.events().subscribe(SettingChanged.class, notifications::onSettingChanged);
        hud.online().forEach(hud::apply);
    }

    @Override
    public void shutdown() {
        if (changes != null) {
            changes.close();
        }
        if (notificationChanges != null) {
            notificationChanges.close();
        }
    }
}
