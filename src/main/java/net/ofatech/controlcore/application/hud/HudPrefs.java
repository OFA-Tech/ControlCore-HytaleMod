package net.ofatech.controlcore.application.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Result;

public final class HudPrefs {

    static final SettingId HIDDEN = new SettingId(HudService.NAMESPACE, "hud-hidden");

    private final HudService hud;

    public HudPrefs(HudService hud) {
        this.hud = hud;
    }

    public SettingsEditor around(SettingsEditor inner) {
        return new SettingsEditor() {
            @Override
            public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
                List<SettingSnapshot> snapshots = inner.snapshot(scope, player);
                if (scope != SettingScope.PLAYER || player.isEmpty()) {
                    return snapshots;
                }
                for (int index = 0; index < snapshots.size(); index++) {
                    SettingId id = snapshots.get(index).id();
                    if (id.namespace().equals(HudService.NAMESPACE) && id.key().startsWith(HudService.PART_PREFIX)) {
                        List<SettingSnapshot> shown = new ArrayList<>(snapshots);
                        shown.add(
                                index, shown(player.get(), snapshots.get(index).definition()));
                        return shown;
                    }
                }
                return snapshots;
            }

            @Override
            public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
                return owns(id, player) ? Result.success(shown(player.get(), anchor(player))) : inner.read(id, player);
            }

            @Override
            public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
                if (!owns(id, player)) {
                    return inner.write(id, player, text);
                }
                return SettingType.bool().parse(text).map(hidden -> {
                    hud.setHidden(player.get(), hidden);
                    return shown(player.get(), anchor(player));
                });
            }

            @Override
            public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
                return owns(id, player) ? write(id, player, "false") : inner.reset(id, player);
            }

            @Override
            public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
                return inner.onAnyChange(listener);
            }

            private SettingDefinition<?> anchor(Optional<PlayerId> player) {
                return inner.snapshot(SettingScope.PLAYER, player).stream()
                        .filter(snapshot -> snapshot.id().namespace().equals(HudService.NAMESPACE)
                                && snapshot.id().key().startsWith(HudService.PART_PREFIX))
                        .findFirst()
                        .map(SettingSnapshot::definition)
                        .orElse(null);
            }
        };
    }

    private static boolean owns(SettingId id, Optional<PlayerId> player) {
        return HIDDEN.equals(id) && player.isPresent();
    }

    private SettingSnapshot shown(PlayerId player, SettingDefinition<?> anchor) {
        SettingDefinition<Boolean> definition = SettingDefinition.player(
                        HIDDEN.namespace(), HIDDEN.key(), SettingType.bool(), false)
                .named(Text.key("controlcore.settings.hud_hidden.name", "Hide the whole HUD"))
                .describedAs(Text.key(
                        "controlcore.settings.hud_hidden.desc",
                        "Hides every part of the HUD until you turn it back on or leave; same as /cc-hud."))
                .inCategory(Text.key("controlcore.hud.category", "HUD"));
        if (anchor != null) {
            definition = definition.withModName(anchor.modNameText());
        }
        boolean hidden = hud.isHidden(player);
        return new SettingSnapshot(definition, String.valueOf(hidden), hidden);
    }
}
