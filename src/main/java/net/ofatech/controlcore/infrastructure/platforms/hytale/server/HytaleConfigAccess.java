package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import java.util.List;
import java.util.Set;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;

/**
 * Which keys of Hytale's {@code config.json} the control panel may change. Keys the server reads while running
 * apply straight away; keys read at startup apply after a restart; everything else, including keys added by
 * future Hytale versions, is read-only until it is listed here.
 */
final class HytaleConfigAccess {

    /** Read by the server while it runs (on connect, per tick). A key ending in {@code .*} covers a section. */
    private static final List<String> LIVE = List.of(
            "ServerName", "MOTD", "MaxPlayers", "MaxViewRadius", "ConnectionTimeouts.*", "RequireJoinPermission");

    /** Read at startup; saved now, applied after a restart. */
    private static final List<String> RESTART = List.of(
            "Defaults.World",
            "Defaults.GameMode",
            "Defaults.HardcoreMode",
            "RateLimit.*",
            "Backup.*",
            "Update.*",
            "WorldMap.*",
            "DisplayTmpTagsInStrings");

    /** Never editable, whatever the lists above say. */
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
