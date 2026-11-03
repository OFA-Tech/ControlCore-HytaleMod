package net.ofatech.controlcore.application.settings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.ofatech.controlcore.application.keymaps.KeymapsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.snapshot.SnapshotService;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.settings.DefaultsView;
import net.ofatech.controlcore.domain.model.settings.DefaultsView.Section;
import net.ofatech.controlcore.domain.model.settings.PermissionsView;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSearch;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot;
import net.ofatech.controlcore.domain.model.snapshot.Snapshot.Part;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class DefaultsService {

    private record Target(SettingsEditor editor, SettingScope scope, Optional<PlayerId> player) {}

    private final SettingsRegistry settings;
    private final ServerConfigEditor serverConfig;
    private final KeymapsEditor keymaps;
    private final SettingPermissionPolicy policy;
    private final PermissionsEditor permissions;
    private final SnapshotService snapshots;
    private final Texts texts;

    public DefaultsService(
            SettingsRegistry settings,
            ServerConfigEditor serverConfig,
            KeymapsEditor keymaps,
            SettingPermissionPolicy policy,
            PermissionsEditor permissions,
            SnapshotService snapshots,
            Texts texts) {
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.policy = policy;
        this.permissions = permissions;
        this.snapshots = snapshots;
        this.texts = texts;
    }

    public DefaultsView viewedBy(PlayerId viewer, String name) {
        Optional<PlayerId> who = Optional.of(viewer);
        Actor actor = Actor.player(viewer, name);
        Translator tr = texts.forPlayer(who);
        return new DefaultsView() {
            @Override
            public int count(Section section, Predicate<String> shown) {
                return DefaultsService.this.count(section, shown, who);
            }

            @Override
            public boolean savesFirst(Section section) {
                return DefaultsService.this.savesFirst(section, who);
            }

            @Override
            public Result<String> restore(Section section, Predicate<String> shown) {
                return DefaultsService.this.restore(section, shown, who, actor, tr);
            }
        };
    }

    public int count(Section section, Predicate<String> shown, Optional<PlayerId> who) {
        Result<Integer> counted = check(section, shown, who);
        return counted.isSuccess() ? counted.value() : 0;
    }

    public Result<Integer> check(Section section, Predicate<String> shown, Optional<PlayerId> who) {
        if (section == Section.PERMISSIONS) {
            return admin(who)
                    ? Result.success(ruled(shown, texts.forPlayer(who)).size())
                    : forbidden();
        }
        return target(section, who).map(target -> resettable(target, shown).size());
    }

    public boolean savesFirst(Section section, Optional<PlayerId> who) {
        return section.serverWide() && snapshots.mayUse(who);
    }

    public Result<String> restore(
            Section section, Predicate<String> shown, Optional<PlayerId> who, Actor actor, Translator tr) {
        List<String> rows = new ArrayList<>();
        Optional<Target> target = Optional.empty();
        if (section == Section.PERMISSIONS) {
            if (!admin(who)) {
                return forbidden();
            }
            ruled(shown, tr).forEach(entry -> rows.add(entry.target()));
        } else {
            Result<Target> found = target(section, who);
            if (found.isFailure()) {
                return Result.failure(found.error());
            }
            target = Optional.of(found.value());
            resettable(found.value(), shown)
                    .forEach(snapshot -> rows.add(snapshot.id().toString()));
        }
        if (rows.isEmpty()) {
            return Result.success(tr.t("controlcore.panel.defaults.nothing"));
        }
        String safety = "";
        if (savesFirst(section, who)) {
            Result<Snapshot> saved = snapshots.saveBefore("defaults", parts(section), Set.of(), who, actor);
            if (saved.isFailure()) {
                return Result.failure(saved.error());
            }
            safety = " "
                    + tr.t(
                            "controlcore.snapshots.safety_saved",
                            "name",
                            saved.value().name());
        }
        int done = 0;
        if (target.isEmpty()) {
            PermissionsView view = permissions.viewedBy(tr);
            for (String row : rows) {
                done += view.reset(row).isSuccess() ? 1 : 0;
            }
        } else {
            for (SettingSnapshot snapshot : resettable(target.get(), rows::contains)) {
                done += target.get()
                                .editor()
                                .reset(snapshot.id(), target.get().player())
                                .isSuccess()
                        ? 1
                        : 0;
            }
        }
        return Result.success(tr.t("controlcore.panel.defaults.done", "count", done) + safety);
    }

    public Predicate<String> matching(
            Section section, Optional<String> world, String search, Optional<PlayerId> who, Translator tr) {
        SettingSearch wanted = SettingSearch.parse(search);
        Set<String> ids = new HashSet<>();
        if (section == Section.PERMISSIONS) {
            if (admin(who)) {
                permissions.viewedBy(tr).list().stream()
                        .filter(entry -> entry.matches(wanted))
                        .forEach(entry -> ids.add(entry.target()));
            }
            return ids::contains;
        }
        target(section, who)
                .onSuccess(target -> target.editor().snapshot(target.scope(), target.player()).stream()
                        .filter(snapshot ->
                                section != Section.SERVER_CONFIGS || ServerConfigEditor.inWorld(snapshot.id(), world))
                        .filter(snapshot -> wanted.matches(snapshot, tr))
                        .forEach(snapshot -> ids.add(snapshot.id().toString())));
        return ids::contains;
    }

    private static List<SettingSnapshot> resettable(Target target, Predicate<String> shown) {
        return target.editor().snapshot(target.scope(), target.player()).stream()
                .filter(snapshot -> snapshot.overridden()
                        && snapshot.access() != SettingAccess.READ_ONLY
                        && shown.test(snapshot.id().toString()))
                .toList();
    }

    private List<PermissionsView.Entry> ruled(Predicate<String> shown, Translator tr) {
        return permissions.viewedBy(tr).list().stream()
                .filter(entry -> (!entry.view().isBlank() || !entry.edit().isBlank()) && shown.test(entry.target()))
                .toList();
    }

    private Result<Target> target(Section section, Optional<PlayerId> who) {
        return switch (section) {
            case MOD_SETTINGS ->
                Result.success(new Target(policy.editor(settings, who), SettingScope.SERVER, Optional.empty()));
            case SERVER_CONFIGS ->
                Result.success(new Target(policy.editor(serverConfig, who), SettingScope.SERVER, Optional.empty()));
            case PREFS ->
                who.isEmpty()
                        ? playersOnly()
                        : Result.success(new Target(policy.editor(settings, who), SettingScope.PLAYER, who));
            case KEYMAPS_MINE ->
                who.isEmpty() ? playersOnly() : Result.success(new Target(keys(who.get()), SettingScope.PLAYER, who));
            case KEYMAPS_SERVER ->
                !admin(who)
                        ? forbidden()
                        : Result.success(new Target(
                                who.<SettingsEditor>map(this::keys).orElse(keymaps),
                                SettingScope.SERVER,
                                Optional.empty()));
            case PERMISSIONS -> forbidden();
        };
    }

    private KeymapsEditor keys(PlayerId player) {
        return keymaps.viewedBy(player, policy);
    }

    private boolean admin(Optional<PlayerId> who) {
        return who.isEmpty() || policy.isAdmin(who.get());
    }

    static Set<Part> parts(Section section) {
        return switch (section) {
            case SERVER_CONFIGS -> EnumSet.of(Part.CONFIG, Part.WORLDS);
            case KEYMAPS_SERVER -> EnumSet.of(Part.KEYS);
            case PERMISSIONS -> EnumSet.of(Part.PERMISSIONS);
            default -> EnumSet.of(Part.SETTINGS);
        };
    }

    private static <T> Result<T> forbidden() {
        return Result.failure(Error.forbidden(
                "Defaults.Forbidden",
                Text.key("controlcore.errors.defaults.forbidden", "Only admins can restore that section.")));
    }

    private static <T> Result<T> playersOnly() {
        return Result.failure(Error.validation(
                "Commands.PlayersOnly",
                Text.key("controlcore.commands.players_only", "Only players can use this command.")));
    }
}
