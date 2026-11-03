package net.ofatech.controlcore.api;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.result.Result;

/**
 * Player management, as in the control panel's Players page and the player commands ({@code /cc-kick},
 * {@code /cc-ban}, {@code /cc-whitelist}, {@code /cc-group}, ...): listing and inspecting players, moderation,
 * whitelist, operators and groups. Get it with {@link ControlCoreApi#players()}.
 *
 * <p>Players are named by name or UUID text, as in the commands; offline players are found by the name they last
 * used. Methods that may need to look a player up return a {@link CompletableFuture}; it completes on another
 * thread when Hytale has to be asked.
 *
 * <p>The API acts with full rights, like the console, and changes show in the change history as made by a mod.
 * {@link #actingFor(PlayerId)} gives the same API with a player's rights. {@link #teleportTo(String)} and
 * {@link #bring(String)} move the acting player, so without {@code actingFor} they fail with
 * {@code Players.PlayersOnly}.
 *
 * @since API 2.4.0
 */
public interface PlayersApi {

    /**
     * Returns this API acting for a player, with the rights they have on the Players page: each action needs its
     * {@code ofatech.controlcore.players.*} node (or admin, see {@link #rights()}), groups and operators are for
     * admins, only admins may act on admins, and nobody may act on themselves where the panel forbids it. The
     * change history records the player as the one who made the change, and the target sees their name in toasts.
     *
     * @param player the player whose rights apply
     * @return the players API restricted to {@code player}'s rights
     * @throws NullPointerException if {@code player} is {@code null}
     */
    PlayersApi actingFor(PlayerId player);

    /**
     * Tells which actions this API may take.
     *
     * @return the rights; all {@code true} without {@link #actingFor(PlayerId)}
     */
    Rights rights();

    /**
     * Lists the players the server knows: online, seen before, banned, whitelisted or in a group.
     *
     * @param filter which players to keep
     * @param search only players whose name or UUID contains this text (case does not matter); {@code null} or
     *     blank for all
     * @return the matching players; empty if this API may not see players
     */
    List<Player> list(Filter filter, String search);

    /**
     * Reads a player's details page: status, groups, sessions, location and, with the right, inventory.
     *
     * @param player the player's name or UUID
     * @return the details, or an error such as {@code Players.Unknown} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<Details>> details(String player);

    /**
     * Reads what an online player carries, as on their details page.
     *
     * @param player the player's name or UUID
     * @return the inventory (empty and not readable when the player is offline), or an error such as
     *     {@code Players.Unknown}, {@code Players.Forbidden} or {@code Players.ProtectedAdmin}
     */
    CompletableFuture<Result<Inventory>> inventory(String player);

    /**
     * Reads the recent chat, as the Players page shows it.
     *
     * @return the latest chat messages, oldest first; empty if this API may not see players
     */
    List<ChatLine> recentChat();

    /**
     * Kicks an online player.
     *
     * @param player the player's name or UUID
     * @param reason the reason shown to them; empty for none
     * @return a confirmation message, or an error such as {@code Players.NotOnline} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> kick(String player, String reason);

    /**
     * Bans a player; they don't need to be online.
     *
     * @param player the player's name or UUID
     * @param length {@code permanent}, or a number with {@code m}, {@code h}, {@code d} or {@code w}, for example
     *     {@code 7d}
     * @param reason the reason shown to them; empty for none
     * @return a confirmation message, or an error such as {@code Players.Unknown}, {@code Bans.BadLength} or
     *     {@code Bans.Forbidden}
     */
    CompletableFuture<Result<String>> ban(String player, String length, String reason);

    /**
     * Lifts a player's ban.
     *
     * @param player the player's name or UUID
     * @return a confirmation message, or an error such as {@code Bans.NotBanned} or {@code Bans.Forbidden}
     */
    CompletableFuture<Result<String>> unban(String player);

    /**
     * Mutes a player: their chat messages are not sent.
     *
     * @param player the player's name or UUID
     * @param length {@code permanent}, or a number with {@code m}, {@code h}, {@code d} or {@code w}, for example
     *     {@code 30m}
     * @return a confirmation message, or an error such as {@code Players.Unknown} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> mute(String player, String length);

    /**
     * Unmutes a player.
     *
     * @param player the player's name or UUID
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> unmute(String player);

    /**
     * Freezes a player in place.
     *
     * @param player the player's name or UUID
     * @return a confirmation message, or an error such as {@code Players.Unknown} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> freeze(String player);

    /**
     * Lets a frozen player move again.
     *
     * @param player the player's name or UUID
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> unfreeze(String player);

    /**
     * Switches an online player between Adventure and Creative.
     *
     * @param player the player's name or UUID
     * @return a message with the new game mode, or an error such as {@code Players.NotOnline} or
     *     {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> toggleGameMode(String player);

    /**
     * Teleports the acting player to another online player. Needs {@link #actingFor(PlayerId)}.
     *
     * @param player the name or UUID of the player to go to
     * @return a confirmation message, or an error such as {@code Players.PlayersOnly}, {@code Players.NotOnline}
     *     or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> teleportTo(String player);

    /**
     * Brings another online player to the acting player. Needs {@link #actingFor(PlayerId)}.
     *
     * @param player the name or UUID of the player to bring
     * @return a confirmation message, or an error such as {@code Players.PlayersOnly}, {@code Players.NotOnline}
     *     or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> bring(String player);

    /**
     * Adds a player to the whitelist or removes them.
     *
     * @param player the player's name or UUID
     * @param add {@code true} to add, {@code false} to remove
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> whitelist(String player, boolean add);

    /**
     * Tells whether only whitelisted players may join.
     *
     * @return the {@code config.json} key {@code RequireJoinPermission}
     */
    boolean whitelistRequired();

    /**
     * Turns requiring the whitelist on or off (the {@code config.json} key {@code RequireJoinPermission}).
     *
     * @param on {@code true} to let only whitelisted players join
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    Result<String> requireWhitelist(boolean on);

    /**
     * Makes a player an operator or removes them from the operators.
     *
     * @param player the player's name or UUID
     * @param add {@code true} to make them an operator, {@code false} to remove them
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> operator(String player, boolean add);

    /**
     * Lists the permission groups.
     *
     * @return the group ids, for example {@code hytale:Adventurer}; empty if this API may not change groups
     */
    List<String> groups();

    /**
     * Adds a player to a group or removes them from it.
     *
     * @param player the player's name or UUID
     * @param group the group's id, for example {@code hytale:Adventurer}
     * @param add {@code true} to add, {@code false} to remove
     * @return a confirmation message, or an error such as {@code Players.UnknownGroup} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> group(String player, String group, boolean add);

    /**
     * Makes a group the only group of a player: adds it and removes every other one.
     *
     * @param player the player's name or UUID
     * @param group the group's id, for example {@code hytale:Adventurer}
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    CompletableFuture<Result<String>> setOnlyGroup(String player, String group);

    /**
     * Lists the permission nodes a group gives.
     *
     * @param group the group's id, for example {@code hytale:Adventurer}
     * @return the group's nodes; empty if there are none or this API may not change groups
     */
    List<String> groupPermissions(String group);

    /**
     * Gives a permission node to a group or takes it away.
     *
     * @param group the group's id, for example {@code hytale:Adventurer}
     * @param node the permission node
     * @param add {@code true} to give, {@code false} to take away
     * @return a confirmation message, or an error such as {@code Players.NothingToDo} or {@code Players.Forbidden}
     */
    Result<String> groupPermission(String group, String node, boolean add);

    /** Which players {@link #list(Filter, String)} keeps. */
    enum Filter {
        /** Every player. */
        ALL,
        /** Players online now. */
        ONLINE,
        /** Players not online. */
        OFFLINE,
        /** Banned players. */
        BANNED,
        /** Whitelisted players. */
        WHITELISTED,
        /** Operators. */
        OPERATORS,
        /** Muted players. */
        MUTED,
        /** Frozen players. */
        FROZEN
    }

    /**
     * Which actions an API may take.
     *
     * @param kick may kick
     * @param gameMode may switch game modes
     * @param teleport may teleport to and bring players
     * @param ban may ban and unban
     * @param whitelist may change the whitelist and require it
     * @param groups may change operators, groups and group permissions
     * @param mute may mute and unmute
     * @param freeze may freeze and unfreeze
     * @param inventory may see inventories
     */
    record Rights(
            boolean kick,
            boolean gameMode,
            boolean teleport,
            boolean ban,
            boolean whitelist,
            boolean groups,
            boolean mute,
            boolean freeze,
            boolean inventory) {}

    /**
     * A player the server knows.
     *
     * @param id the player's id
     * @param name the player's last known name; empty if unknown
     * @param online whether they are online
     * @param banned whether they are banned
     * @param whitelisted whether they are on the whitelist
     * @param operator whether they are an operator
     * @param muted whether they are muted
     * @param frozen whether they are frozen
     * @param status their status line (online, last seen, groups), in the reader's language
     * @param ban their ban (length and reason), in the reader's language; empty if not banned
     */
    record Player(
            PlayerId id,
            String name,
            boolean online,
            boolean banned,
            boolean whitelisted,
            boolean operator,
            boolean muted,
            boolean frozen,
            String status,
            String ban) {}

    /**
     * A player's details page.
     *
     * @param title the page's title, the player's name
     * @param lines the page's lines, in the reader's language
     */
    record Details(String title, List<Line> lines) {}

    /**
     * One line of a details page.
     *
     * @param text the line's text
     * @param heading whether it starts a part of the page, for example "Inventory"
     */
    record Line(String text, boolean heading) {}

    /**
     * What a player carries.
     *
     * @param readable whether the inventory was actually read; {@code false} (for example the player is offline)
     *     means {@code items} is empty because it is unknown
     * @param items every stack they carry, slot by slot
     */
    record Inventory(boolean readable, List<InventoryItem> items) {

        /**
         * Copies the list so the inventory can't change.
         *
         * @param readable whether the inventory was actually read
         * @param items every stack they carry, slot by slot
         */
        public Inventory {
            items = List.copyOf(items);
        }
    }

    /**
     * One stack in one inventory slot.
     *
     * @param section the part of the inventory: {@code hotbar}, {@code storage}, {@code armor}, {@code utility},
     *     {@code tools} or {@code backpack}
     * @param slot the slot's index in that section, from 0
     * @param itemId the item's id, for example {@code Weapon_Sword_Iron}
     * @param amount how many
     */
    record InventoryItem(String section, int slot, String itemId, int amount) {}

    /**
     * One chat message.
     *
     * @param time when it was sent
     * @param player who sent it
     * @param playerName their name when they sent it
     * @param text what they wrote, on one line
     */
    record ChatLine(Instant time, PlayerId player, String playerName, String text) {}
}
