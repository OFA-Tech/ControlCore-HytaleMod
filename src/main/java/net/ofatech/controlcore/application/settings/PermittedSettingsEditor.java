package net.ofatech.controlcore.application.settings;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingRights;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * A {@link SettingsEditor} as one viewer may use it: settings they may not see are left out (and reported as
 * unknown), settings they may only see are read-only, and changes need the right to change.
 */
public final class PermittedSettingsEditor implements SettingsEditor {

    private final SettingsEditor settings;
    private final SettingPermissionPolicy policy;
    private final Optional<PlayerId> viewer;

    /**
     * @param viewer who uses the editor; empty for the console (may do everything)
     */
    public PermittedSettingsEditor(SettingsEditor settings, SettingPermissionPolicy policy, Optional<PlayerId> viewer) {
        this.settings = settings;
        this.policy = policy;
        this.viewer = viewer;
    }

    @Override
    public List<SettingSnapshot> snapshot(SettingScope scope, Optional<PlayerId> player) {
        return settings.snapshot(scope, player).stream()
                .filter(snapshot -> rights(snapshot).canView())
                .map(this::limited)
                .toList();
    }

    @Override
    public Result<SettingSnapshot> read(SettingId id, Optional<PlayerId> player) {
        return settings.read(id, player)
                .flatMap(snapshot -> rights(snapshot).canView() ? Result.success(limited(snapshot)) : unknown(id));
    }

    @Override
    public Result<SettingSnapshot> write(SettingId id, Optional<PlayerId> player, String text) {
        return allowedToChange(id, player)
                .flatMap(ok -> settings.write(id, player, text))
                .map(this::limited);
    }

    @Override
    public Result<SettingSnapshot> reset(SettingId id, Optional<PlayerId> player) {
        return allowedToChange(id, player)
                .flatMap(ok -> settings.reset(id, player))
                .map(this::limited);
    }

    @Override
    public Subscription onAnyChange(Consumer<? super SettingChanged> listener) {
        return settings.onAnyChange(listener);
    }

    /** @return whether the viewer may see at least one setting of {@code scope} */
    public boolean seesAny(SettingScope scope) {
        return !snapshot(scope, viewer).isEmpty();
    }

    private Result<Boolean> allowedToChange(SettingId id, Optional<PlayerId> player) {
        return settings.read(id, player).flatMap(snapshot -> {
            SettingRights rights = rights(snapshot);
            if (!rights.canView()) {
                return unknown(id);
            }
            if (!rights.canEdit()) {
                return Result.failure(Error.forbidden(
                        "Settings.Forbidden",
                        "You may not change " + snapshot.definition().displayName() + "."));
            }
            return Result.success(true);
        });
    }

    private SettingRights rights(SettingSnapshot snapshot) {
        return policy.rights(viewer, snapshot.definition());
    }

    private SettingSnapshot limited(SettingSnapshot snapshot) {
        if (rights(snapshot).canEdit() || snapshot.access() == SettingAccess.READ_ONLY) {
            return snapshot;
        }
        return new SettingSnapshot(
                snapshot.definition(), snapshot.value(), snapshot.overridden(), SettingAccess.READ_ONLY);
    }

    private static <T> Result<T> unknown(SettingId id) {
        return Result.failure(Error.notFound("Settings.UnknownSetting", "There is no setting " + id + "."));
    }
}
