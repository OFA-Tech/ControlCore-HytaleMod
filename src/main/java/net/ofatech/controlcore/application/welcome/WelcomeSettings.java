package net.ofatech.controlcore.application.welcome;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;

/** Settings of the join message. */
public final class WelcomeSettings {

    static final String MESSAGE_ID = "controlcore:welcome-message";
    static final String SHOW_ID = "controlcore:show-welcome-message";

    private final Setting<String> message;
    private final Setting<Boolean> show;

    public WelcomeSettings(SettingsApi settings) {
        this.message = settings.register(SettingDefinition.server(MESSAGE_ID, SettingType.text(256), "")
                        .named("Welcome message")
                        .describedAs("Sent to players when they join; {player} is replaced by their name. Empty"
                                + " sends nothing.")
                        .inCategory("ControlCore"))
                .value();
        this.show = settings.register(SettingDefinition.player(SHOW_ID, SettingType.bool(), true)
                        .named("Show welcome message")
                        .describedAs("Whether you get the server's welcome message when you join.")
                        .inCategory("ControlCore"))
                .value();
    }

    /** @return the message for this player, with {@code {player}} filled in; empty if none or turned off */
    public String messageFor(PlayerId player, String name) {
        String text = message.get().strip();
        return text.isEmpty() || !show.get(player) ? "" : text.replace("{player}", name);
    }
}
