package net.ofatech.controlcore.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.model.PlayerInfo;
import net.ofatech.controlcore.domain.model.hud.HudElement;
import net.ofatech.controlcore.domain.model.hud.HudPart;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import org.junit.jupiter.api.Test;

class HudRuntimeTest extends RuntimeTestBase {

    @Test
    void hudPartsFollowPreferencesServerRulesAndTheHideAllToggle() {
        runtime.setup();
        runtime.start();
        PlayerInfo player = platform.players.join("Player");
        var hud = ControlCoreApi.get().huds();
        var shown = new ArrayList<String>();
        hud.register(HudElement.of("minimap", "map")
                .displayName("Minimap")
                .onShow(id -> shown.add("show"))
                .onHide(id -> shown.add("hide")));
        platform.connectedPublisher.publish(new PlayerJoined(player));
        var compass = HudPart.COMPASS;
        var chat = HudPart.CHAT;

        assertThat(platform.hiddenHud.getOrDefault(player.id(), Set.of())).isEmpty();
        assertThat(shown).containsExactly("show");

        platform.commands.run("cc-pref", player, Map.of("id", "controlcore:hud.compass", "value", "false"));
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(compass);

        PlayerInfo admin = platform.players.join("Admin");
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:hud-rule.chat", "value", "Always hidden"));
        platform.commands.run("cc-set", admin, Map.of("id", "controlcore:hud-rule.compass", "value", "Always shown"));
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(chat);

        hud.setHidden(player.id(), true);
        assertThat(platform.hiddenHud.get(player.id())).containsExactlyInAnyOrder(HudPart.values());
        assertThat(hud.isVisible(player.id(), SettingId.of("minimap:map"))).isFalse();
        hud.setHidden(player.id(), false);
        assertThat(platform.hiddenHud.get(player.id())).containsExactly(chat);
        assertThat(shown).containsExactly("show", "hide", "show");

        platform.commands.run("cc-pref", player, Map.of("id", "minimap:hud.map", "value", "false"));
        assertThat(shown).containsExactly("show", "hide", "show", "hide");
        assertThat(ControlCoreApi.get().keymaps().find(SettingId.of("controlcore:toggle-hud")))
                .isPresent();
    }
}
