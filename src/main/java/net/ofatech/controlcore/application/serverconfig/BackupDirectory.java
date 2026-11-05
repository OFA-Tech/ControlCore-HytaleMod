package net.ofatech.controlcore.application.serverconfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;

public final class BackupDirectory {

    public static final SettingId ID = ServerConfigEditor.idOf("Backup.Directory");

    private BackupDirectory() {}

    public static boolean allowed(String value, Path universe) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.startsWith("\\\\") || text.startsWith("//")) {
            return false;
        }
        for (String part : text.split("[/\\\\]")) {
            if (part.strip().equals("..")) {
                return false;
            }
        }
        try {
            Path path = Path.of(text);
            if (text.contains(":") && !(path.isAbsolute() && text.lastIndexOf(':') == 1)) {
                return false;
            }
            if ((text.startsWith("/") || text.startsWith("\\")) && !path.isAbsolute()) {
                return false;
            }
            Path server = Path.of("").toAbsolutePath().normalize();
            Path resolved = server.resolve(path).normalize();
            Path world = server.resolve(universe).normalize();
            if (resolved.getParent() == null || resolved.equals(server) || resolved.startsWith(world)) {
                return false;
            }
            return !Files.exists(resolved)
                    || !Files.exists(world)
                    || !real(resolved).startsWith(real(world));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    public static Error bad(String value) {
        return Error.validation(
                "Backup.BadDirectory",
                Text.key(
                                "controlcore.errors.backup.bad_directory",
                                "'{directory}' can't be the backup directory: use a folder inside the server folder (for example backups) or a local folder such as D:/Backups, not a network share, not .. and not the universe folder. It can also be set in config.json (Backup.Directory) while the server is stopped.")
                        .with("directory", value));
    }

    public static Error forbidden() {
        return Error.forbidden(
                "Backup.DirectoryForbidden",
                Text.key(
                        "controlcore.errors.backup.directory_forbidden",
                        "Only admins and the console may change the backup directory. Ask an admin, or set it from the server console with /cc-backup --directory=<folder>."));
    }
}
