package net.ofatech.controlcore.api;

import java.util.List;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * The server's health, as in the control panel's Health tab and {@code /cc-diagnostics}: server info, each world's
 * TPS and chunks, mods that failed, and recent problems. Get it with {@link ControlCoreApi#health()}.
 *
 * <p>The API acts with full rights, like the console. {@link #actingFor(PlayerId)} gives the same API with a
 * player's rights: only admins may see it.
 *
 * @since API 2.4.0
 */
public interface HealthApi {

    /**
     * Returns this API acting for a player. Only admins may use it: for anyone else {@link #sections()} is empty
     * and {@link #writeReport()} fails with {@code Diagnostics.Forbidden}. Texts are in the player's language.
     *
     * @param player the player whose rights apply
     * @return the health API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    HealthApi actingFor(PlayerId player);

    /**
     * Reads the server's health now, as the Health tab shows it.
     *
     * @return the sections in display order (server, worlds, mods, recent problems); empty if this API may not
     *     see them
     */
    List<Section> sections();

    /**
     * Writes a diagnostics report to a text file, with passwords, tokens and IP addresses removed.
     *
     * @return the path of the written file, or an error such as {@code Diagnostics.Forbidden}
     */
    Result<String> writeReport();

    /**
     * One part of the Health tab.
     *
     * @param title the section's title, in the reader's language
     * @param lines the section's lines
     */
    record Section(String title, List<Line> lines) {}

    /**
     * One value of the Health tab.
     *
     * @param label what is measured, in the reader's language
     * @param value the measured value as shown, for example {@code 19.8 TPS}
     * @param warning whether the value needs attention, for example TPS below the target
     */
    record Line(String label, String value, boolean warning) {}
}
