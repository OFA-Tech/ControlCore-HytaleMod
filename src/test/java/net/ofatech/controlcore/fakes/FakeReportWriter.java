package net.ofatech.controlcore.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.ofatech.controlcore.domain.ports.config.ReportWriter;
import net.ofatech.controlcore.domain.result.Result;

public final class FakeReportWriter implements ReportWriter {

    public final Map<String, String> files = new LinkedHashMap<>();

    @Override
    public Result<String> write(String fileName, String text) {
        files.put(fileName, text);
        return Result.success("diagnostics/" + fileName);
    }
}
