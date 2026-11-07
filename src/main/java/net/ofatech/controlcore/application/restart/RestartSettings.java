package net.ofatech.controlcore.application.restart;

import java.time.Duration;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.application.settings.SettingsRegistry;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

public final class RestartSettings {

    private static final Text CATEGORY = Text.key("controlcore.settings.category.restarts", "Restarts");

    private final Setting<Integer> countdown;
    private final Setting<Boolean> backupFirst;
    private final Setting<String> schedule;

    public RestartSettings(SettingsRegistry settings) {
        this.countdown = settings.register(
                        SettingDefinition.server("controlcore", "restart-countdown", SettingType.integer(10, 600), 60)
                                .named(Text.key("controlcore.settings.restart_countdown.name", "Restart countdown"))
                                .describedAs(
                                        Text.key(
                                                "controlcore.settings.restart_countdown.desc",
                                                "Seconds between pressing Restart (or a scheduled restart) and the server stopping. Players are warned on the way."))
                                .inCategory(CATEGORY))
                .value();
        this.backupFirst = settings.register(SettingDefinition.server(
                                "controlcore", "restart-backup-first", SettingType.bool(), false)
                        .named(Text.key("controlcore.settings.restart_backup_first.name", "Back up before restarting"))
                        .describedAs(
                                Text.key(
                                        "controlcore.settings.restart_backup_first.desc",
                                        "Make a backup when the countdown ends, before the server stops. If it fails, the server still restarts."))
                        .inCategory(CATEGORY))
                .value();
        this.schedule = settings.register(
                        SettingDefinition.server("controlcore", "restart-schedule", SettingType.text(256), "")
                                .named(Text.key("controlcore.settings.restart_schedule.name", "Scheduled restarts"))
                                .describedAs(
                                        Text.key(
                                                "controlcore.settings.restart_schedule.desc",
                                                "Daily times (HH:mm) or cron schedules, separated by ;, for example 04:00; 0 16 * * SAT. The countdown starts early so the server stops at that time."))
                                .inCategory(CATEGORY))
                .value();
    }

    public Duration countdown() {
        return Duration.ofSeconds(countdown.get());
    }

    public boolean backupFirst() {
        return backupFirst.get();
    }

    public String schedule() {
        return schedule.get();
    }
}
