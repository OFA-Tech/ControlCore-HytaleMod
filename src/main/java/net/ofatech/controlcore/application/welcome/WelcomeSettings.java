package net.ofatech.controlcore.application.welcome;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

public final class WelcomeSettings {

    static final String MESSAGE_ID = "controlcore:welcome-message";
    static final String SHOW_ID = "controlcore:show-welcome-message";

    private final Setting<String> message;
    private final Setting<Boolean> show;
    private final Setting<String> color;

    public WelcomeSettings(SettingsApi settings) {
        this.message = settings.register(
                        SettingDefinition.server("controlcore", "welcome-message", SettingType.text(256), "")
                                .named(Text.key("controlcore.settings.welcome_message.name", "Welcome message"))
                                .describedAs(Text.key(
                                        "controlcore.settings.welcome_message.desc",
                                        "Sent to players when they join; {player} is replaced by their name. Empty"
                                                + " sends nothing."))
                                .coloredBy("controlcore:welcome-message-color"))
                .value();
        this.color = settings.register(SettingDefinition.server(
                                "controlcore", "welcome-message-color", SettingType.color(), "#ffaa00")
                        .named(Text.key("controlcore.settings.welcome_message_color.name", "Welcome message colour"))
                        .describedAs(Text.key(
                                "controlcore.settings.welcome_message_color.desc",
                                "The colour of the welcome message.")))
                .value();
        this.show = settings.register(SettingDefinition.player(
                                "controlcore", "show-welcome-message", SettingType.bool(), true)
                        .named(Text.key("controlcore.settings.show_welcome_message.name", "Show welcome message"))
                        .describedAs(Text.key(
                                "controlcore.settings.show_welcome_message.desc",
                                "Whether you get the server's welcome message when you join.")))
                .value();
    }

    public String color() {
        return color.get();
    }

    public String messageFor(PlayerId player, String name) {
        String text = message.get().strip();
        return text.isEmpty() || !show.get(player) ? "" : text.replace("{player}", name);
    }
}
