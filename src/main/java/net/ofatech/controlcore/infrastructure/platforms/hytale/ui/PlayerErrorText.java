package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Error;

final class PlayerErrorText {

    private PlayerErrorText() {}

    static Error busy(Translator tr) {
        return Error.validation("Players.Busy", tr.t("controlcore.errors.players.busy"));
    }
}
