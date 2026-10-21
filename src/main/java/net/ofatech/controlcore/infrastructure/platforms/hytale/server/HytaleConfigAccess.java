package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import java.util.List;
import java.util.Set;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;

final class HytaleConfigAccess {

    private static final List<String> LIVE = List.of(
            "ServerName", "MOTD", "MaxPlayers", "MaxViewRadius", "ConnectionTimeouts.*", "RequireJoinPermission");

    private static final List<String> RESTART = List.of(
            "Defaults.World",
            "Defaults.GameMode",
            "Defaults.HardcoreMode",
            "RateLimit.*",
            "Backup.*",
            "Update.*",
            "WorldMap.*",
            "DisplayTmpTagsInStrings");

    private static final Set<String> NEVER = Set.of("Version", "Password");

    private HytaleConfigAccess() {}

    static SettingAccess of(String path) {
        if (NEVER.contains(path)) {
            return SettingAccess.READ_ONLY;
        }
        if (matches(LIVE, path)) {
            return SettingAccess.EDITABLE;
        }
        if (matches(RESTART, path)) {
            return SettingAccess.APPLIES_AFTER_RESTART;
        }
        return SettingAccess.READ_ONLY;
    }

    private static boolean matches(List<String> patterns, String path) {
        for (String pattern : patterns) {
            boolean match = pattern.endsWith(".*")
                    ? path.startsWith(pattern.substring(0, pattern.length() - 1))
                    : pattern.equals(path);
            if (match) {
                return true;
            }
        }
        return false;
    }
}
