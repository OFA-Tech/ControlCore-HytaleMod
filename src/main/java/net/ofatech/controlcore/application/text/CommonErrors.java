package net.ofatech.controlcore.application.text;

import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.result.Error;

public final class CommonErrors {

    private CommonErrors() {}

    public static Error notSaved() {
        return Error.failure(
                "Storage.NotSaved",
                Text.key(
                        "controlcore.errors.storage.not_saved",
                        "The change was not saved because its file could not be written. The server log has the"
                                + " details."));
    }
}
