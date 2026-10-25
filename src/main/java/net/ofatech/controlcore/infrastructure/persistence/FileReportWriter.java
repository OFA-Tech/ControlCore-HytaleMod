package net.ofatech.controlcore.infrastructure.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ofatech.controlcore.domain.ports.config.ReportWriter;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class FileReportWriter implements ReportWriter {

    private final Path folder;
    private final String shownFolder;
    private final Log log;

    public FileReportWriter(Path folder, String shownFolder, Log log) {
        this.folder = folder;
        this.shownFolder = shownFolder;
        this.log = log;
    }

    @Override
    public Result<String> write(String fileName, String text) {
        Path file = folder.resolve(fileName);
        try {
            Files.createDirectories(folder);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return Result.success(shownFolder + "/" + fileName);
        } catch (IOException e) {
            log.error("Could not write " + file, e);
            return Result.failure(Error.failure("Diagnostics.WriteFailed", "Could not write " + fileName));
        }
    }
}
