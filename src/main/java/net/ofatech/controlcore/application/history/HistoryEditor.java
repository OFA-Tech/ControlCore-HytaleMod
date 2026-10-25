package net.ofatech.controlcore.application.history;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.application.keymaps.KeymapRegistry;
import net.ofatech.controlcore.application.mods.ModsEditor;
import net.ofatech.controlcore.application.serverconfig.ServerConfigEditor;
import net.ofatech.controlcore.application.settings.SettingPermissionPolicy;
import net.ofatech.controlcore.application.text.Texts;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.history.ChangeRecord;
import net.ofatech.controlcore.domain.model.history.ChangeRecord.Actor;
import net.ofatech.controlcore.domain.model.history.HistoryView;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.ports.game.Permissions;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class HistoryEditor {

    public static final String VIEW = "history";
    public static final String ROLLBACK = "history.rollback";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChangeHistory history;
    private final SettingsApi settings;
    private final ServerConfigEditor serverConfig;
    private final KeymapRegistry keymaps;
    private final SettingPermissionPolicy policy;
    private final ModsEditor mods;
    private final Permissions permissions;
    private final Texts texts;
    private final BansService bans;

    public HistoryEditor(
            ChangeHistory history,
            SettingsApi settings,
            ServerConfigEditor serverConfig,
            KeymapRegistry keymaps,
            SettingPermissionPolicy policy,
            ModsEditor mods,
            Permissions permissions,
            Texts texts,
            BansService bans) {
        this.history = history;
        this.settings = settings;
        this.serverConfig = serverConfig;
        this.keymaps = keymaps;
        this.policy = policy;
        this.mods = mods;
        this.permissions = permissions;
        this.texts = texts;
        this.bans = bans;
    }

    public boolean maySee(Optional<PlayerId> viewer) {
        return viewer.isEmpty() || policy.isAdmin(viewer.get()) || permissions.hasOwn(viewer.get(), VIEW);
    }

    public boolean mayRollBack(Optional<PlayerId> viewer) {
        return viewer.isEmpty() || policy.isAdmin(viewer.get()) || permissions.hasOwn(viewer.get(), ROLLBACK);
    }

    public HistoryView viewedBy(PlayerId viewer, String name) {
        Translator tr = texts.forPlayer(Optional.of(viewer));
        Optional<PlayerId> who = Optional.of(viewer);
        return new HistoryView() {
            @Override
            public List<Entry> entries() {
                return history.newestFirst().stream()
                        .map(record -> entry(record, tr, who))
                        .toList();
            }

            @Override
            public long newest() {
                return history.newestId();
            }

            @Override
            public Set<String> targets() {
                return history.targets();
            }

            @Override
            public boolean canRollback() {
                return mayRollBack(who);
            }

            @Override
            public Result<String> rollback(long id) {
                return HistoryEditor.this.rollback(id, who, Actor.player(viewer, name), tr);
            }
        };
    }

    public List<HistoryView.Entry> entries(Translator tr, Optional<PlayerId> viewer) {
        return history.newestFirst().stream()
                .map(record -> entry(record, tr, viewer))
                .toList();
    }

    public Result<String> rollback(long id, Optional<PlayerId> who, Actor actor, Translator tr) {
        Optional<ChangeRecord> found = history.find(id);
        if (found.isEmpty()) {
            return Result.failure(Error.notFound(
                    "History.Unknown",
                    Text.key("controlcore.errors.history.unknown", "There is no change #{id}.")
                            .with("id", id)));
        }
        ChangeRecord record = found.get();
        if (!mayRollBack(who)) {
            return Result.failure(Error.forbidden(
                    "History.Forbidden",
                    Text.key("controlcore.errors.history.forbidden", "You may not roll changes back.")));
        }
        if (history.rolledBack(id)) {
            return Result.failure(Error.validation(
                    "History.AlreadyRolledBack",
                    Text.key("controlcore.errors.history.already", "Change #{id} was already rolled back.")
                            .with("id", id)));
        }
        Result<?> done = ChangeContext.rollingBack(actor, id, () -> apply(record, who));
        return done.map(ignored -> tr.t(
                "controlcore.history.rolled_back",
                "id",
                id,
                "what",
                what(record, tr),
                "value",
                shown(record.kind(), record.oldValue(), tr)));
    }

    private Result<?> apply(ChangeRecord record, Optional<PlayerId> who) {
        String old = record.oldValue();
        return switch (record.kind()) {
            case SETTING -> write(settings, record.target(), old, who);
            case SERVER_CONFIG -> write(serverConfig, record.target(), old, who);
            case SERVER_KEYS ->
                admin(who)
                        .flatMap(ok -> SettingId.parse(record.target()))
                        .flatMap(id -> keymaps.bindServer(id, old.isEmpty() ? "none" : old));
            case CUSTOM_BIND ->
                admin(who).flatMap(ok -> {
                    String name = record.target().substring(record.target().indexOf(':') + 1);
                    return HistoryValues.bind(old)
                            .<Result<?>>map(
                                    bind -> keymaps.addCustom(name, bind.keys(), bind.command(), bind.runAsServer()))
                            .orElseGet(() -> keymaps.removeCustom(record.target()));
                });
            case PERMISSION ->
                admin(who).flatMap(ok -> {
                    HistoryValues.Rule rule = HistoryValues.rule(old);
                    return policy.replaceRule(record.target(), rule.view(), rule.edit());
                });
            case MOD -> mods.viewedBy(who).setEnabled(record.target(), Boolean.parseBoolean(old));
            case BAN -> admin(who).flatMap(ok -> bans.restore(record.target(), old, who));
        };
    }

    private Result<?> write(SettingsEditor editor, String target, String value, Optional<PlayerId> who) {
        return SettingId.parse(target).flatMap(id -> policy.editor(editor, who).write(id, Optional.empty(), value));
    }

    private Result<Boolean> admin(Optional<PlayerId> who) {
        if (who.isEmpty() || policy.isAdmin(who.get())) {
            return Result.success(true);
        }
        return Result.failure(Error.forbidden(
                "History.AdminOnly",
                Text.key("controlcore.errors.history.admin_only", "Only admins can roll this change back.")));
    }

    private HistoryView.Entry entry(ChangeRecord record, Translator tr, Optional<PlayerId> viewer) {
        boolean rolledBack = history.rolledBack(record.id());
        return new HistoryView.Entry(
                record.id(),
                record.kind(),
                record.target(),
                WHEN.format(record.at().atZone(ZoneId.systemDefault())),
                who(record.actor(), tr),
                tr.t("controlcore.history.source." + record.source().name().toLowerCase(Locale.ROOT)),
                what(record, tr),
                shown(record.kind(), record.oldValue(), tr),
                shown(record.kind(), record.newValue(), tr),
                record.restart(),
                record.isRollback(),
                rolledBack,
                !rolledBack && mayRollBack(viewer));
    }

    public String who(Actor actor, Translator tr) {
        return actor.isMod() ? tr.t("controlcore.history.by_mod") : actor.name();
    }

    public String what(ChangeRecord record, Translator tr) {
        return what(record.kind(), record.target(), tr);
    }

    public String what(ChangeRecord.Kind kind, String target, Translator tr) {
        return switch (kind) {
            case SETTING -> settingName(settings, target, tr);
            case SERVER_CONFIG -> settingName(serverConfig, target, tr);
            case SERVER_KEYS ->
                tr.t(
                        "controlcore.history.what.keys",
                        "name",
                        SettingId.parse(target)
                                .map(id -> keymaps.find(id)
                                        .map(action -> tr.translate(action.nameText()))
                                        .orElse(target))
                                .orElse(target));
            case CUSTOM_BIND ->
                tr.t("controlcore.history.what.custom_bind", "name", target.substring(target.indexOf(':') + 1));
            case PERMISSION -> tr.t("controlcore.history.what.permission", "target", permissionTarget(target, tr));
            case BAN -> bans.what(target, tr);
            case MOD ->
                mods.viewedBy(Optional.empty()).list().stream()
                        .filter(entry -> entry.mod().id().equalsIgnoreCase(target))
                        .findFirst()
                        .map(entry -> entry.mod().name())
                        .orElse(target);
        };
    }

    public String shown(ChangeRecord.Kind kind, String value, Translator tr) {
        return switch (kind) {
            case CUSTOM_BIND ->
                HistoryValues.bind(value)
                        .map(bind -> tr.t(
                                bind.runAsServer()
                                        ? "controlcore.history.value.bind_server"
                                        : "controlcore.history.value.bind",
                                "keys",
                                bind.keys(),
                                "command",
                                bind.command()))
                        .orElse(tr.t("controlcore.history.value.none"));
            case PERMISSION -> {
                HistoryValues.Rule rule = HistoryValues.rule(value);
                if (rule.view().isEmpty() && rule.edit().isEmpty()) {
                    yield tr.t("controlcore.history.value.default_rule");
                }
                String fallback = tr.t("controlcore.history.value.inherit");
                yield tr.t(
                        "controlcore.history.value.rule",
                        "view",
                        rule.view().isEmpty() ? fallback : rule.view(),
                        "edit",
                        rule.edit().isEmpty() ? fallback : rule.edit());
            }
            case MOD ->
                tr.t(Boolean.parseBoolean(value) ? "controlcore.history.value.on" : "controlcore.history.value.off");
            case BAN -> bans.shown(value, tr);
            default -> value.isEmpty() ? tr.t("controlcore.history.value.empty") : value;
        };
    }

    private String permissionTarget(String target, Translator tr) {
        if (target.startsWith(SettingPermissionPolicy.SETTING)) {
            String id = target.substring(SettingPermissionPolicy.SETTING.length());
            String name = settingName(settings, id, tr);
            return name.equals(id) ? settingName(serverConfig, id, tr) : name;
        }
        return target;
    }

    private static String settingName(SettingsEditor editor, String target, Translator tr) {
        return SettingId.parse(target)
                .flatMap(id -> editor.read(id, Optional.empty()))
                .map(snapshot -> snapshot.definition().heading(tr) + " · "
                        + tr.translate(snapshot.definition().nameText()))
                .orElse(target);
    }
}
