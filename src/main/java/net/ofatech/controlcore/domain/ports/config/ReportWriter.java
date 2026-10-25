package net.ofatech.controlcore.domain.ports.config;

import net.ofatech.controlcore.domain.result.Result;

public interface ReportWriter {

    Result<String> write(String fileName, String text);
}
