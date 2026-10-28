package net.ofatech.controlcore.application.players;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.players.PlayerAction;
import net.ofatech.controlcore.domain.model.players.PlayersView;
import net.ofatech.controlcore.domain.model.text.Translator;
import net.ofatech.controlcore.domain.result.Result;

final class PlayersViewer implements PlayersView {

    private final PlayerId viewer;
    private final Optional<PlayerId> who;
    private final Translator tr;
    private final Rights rights;
    private final PlayerListing listing;
    private final PlayerModeration moderation;
    private final PlayerAccess access;
    private final PlayerControl control;
    private final BansService bans;
    private final PlayersChanges changes;

    PlayersViewer(
            PlayerId viewer,
            Translator tr,
            Rights rights,
            PlayerListing listing,
            PlayerModeration moderation,
            PlayerAccess access,
            PlayerControl control,
            BansService bans,
            PlayersChanges changes) {
        this.viewer = viewer;
        this.who = Optional.of(viewer);
        this.tr = tr;
        this.rights = rights;
        this.listing = listing;
        this.moderation = moderation;
        this.access = access;
        this.control = control;
        this.bans = bans;
        this.changes = changes;
    }

    @Override
    public String viewer() {
        return viewer.value().toString();
    }

    @Override
    public Rights rights() {
        return rights;
    }

    @Override
    public List<Entry> list() {
        return listing.list(tr);
    }

    @Override
    public List<Choice> filters() {
        return Arrays.stream(Filter.values())
                .map(filter -> new Choice(filter.id(), tr.t("controlcore.players.filter." + filter.id())))
                .toList();
    }

    @Override
    public List<Choice> lengths() {
        return bans.lengths(tr);
    }

    @Override
    public boolean showHeads() {
        return bans.showHeads();
    }

    @Override
    public CompletableFuture<Optional<String>> resolve(String id) {
        return PlayerDirectory.parse(id).map(bans::resolve).orElse(CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public CompletableFuture<Optional<String>> live(String id) {
        return PlayerDirectory.parse(id)
                .map(found -> listing.live(found, tr))
                .orElse(CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public CompletableFuture<Result<String>> ban(String player, String length, String reason) {
        return bans.ban(who, player, length, reason, tr);
    }

    @Override
    public Result<String> unban(String id) {
        return bans.unban(who, id, tr);
    }

    @Override
    public Result<String> kick(String id, String reason) {
        return PlayerDirectory.parse(id)
                .map(found -> moderation.kick(who, found, reason, tr))
                .orElse(Result.failure(PlayerErrors.failed()));
    }

    @Override
    public CompletableFuture<Result<String>> toggleGameMode(String id) {
        return byId(id, found -> control.toggleGameMode(who, found, tr));
    }

    @Override
    public CompletableFuture<Result<String>> teleportTo(String id) {
        return byId(id, found -> control.teleport(who, viewer, found, false, tr));
    }

    @Override
    public CompletableFuture<Result<String>> bring(String id) {
        return byId(id, found -> control.teleport(who, found, viewer, true, tr));
    }

    @Override
    public List<Choice> groups() {
        return access.groups(who);
    }

    @Override
    public List<String> groupPermissions(String group) {
        return access.groupPermissions(who, group);
    }

    @Override
    public CompletableFuture<Result<String>> whitelist(String player, boolean add) {
        return access.setWhitelisted(who, player, add, tr);
    }

    @Override
    public CompletableFuture<Result<String>> operator(String player, boolean add) {
        return access.setGroup(who, player, access.operatorGroup(), add, tr);
    }

    @Override
    public CompletableFuture<Result<String>> group(String player, String group, boolean add) {
        return access.setGroup(who, player, group, add, tr);
    }

    @Override
    public Result<String> groupPermission(String group, String node, boolean add) {
        return access.setGroupPermission(who, group, node, add, tr);
    }

    @Override
    public CompletableFuture<Result<String>> mute(String player, String length, boolean add) {
        return moderation.setMuted(who, player, length, add, tr);
    }

    @Override
    public CompletableFuture<Result<String>> freeze(String player, boolean add) {
        return moderation.setFrozen(who, player, add, tr);
    }

    @Override
    public CompletableFuture<Result<String>> apply(PlayerAction action, PlayerAction.Input input) {
        if (action.needsPlayer() && PlayerDirectory.clean(input.player()).isEmpty()) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.noPlayer()));
        }
        return switch (action) {
            case BAN -> ban(input.player(), input.length(), input.reason());
            case UNBAN -> PlayerDirectory.completed(unban(input.player()));
            case KICK -> PlayerDirectory.completed(moderation.kickNamed(who, input.player(), input.reason(), tr));
            case WHITELIST_ADD, WHITELIST_REMOVE -> whitelist(input.player(), action == PlayerAction.WHITELIST_ADD);
            case MUTE_ADD, MUTE_REMOVE -> mute(input.player(), input.length(), action == PlayerAction.MUTE_ADD);
            case FREEZE_ADD, FREEZE_REMOVE -> freeze(input.player(), action == PlayerAction.FREEZE_ADD);
            case OP_ADD, OP_REMOVE -> operator(input.player(), action == PlayerAction.OP_ADD);
            case GROUP_ADD, GROUP_REMOVE -> group(input.player(), input.group(), action == PlayerAction.GROUP_ADD);
            case NODE_ADD, NODE_REMOVE ->
                PlayerDirectory.completed(
                        groupPermission(input.group(), input.node(), action == PlayerAction.NODE_ADD));
        };
    }

    @Override
    public CompletableFuture<Result<Details>> details(String id) {
        Optional<PlayerId> found = PlayerDirectory.parse(id);
        if (found.isEmpty()) {
            return PlayerDirectory.completed(Result.failure(PlayerErrors.unknown(PlayerDirectory.clean(id))));
        }
        return listing.details(found.get(), rights.inventory(), tr);
    }

    @Override
    public Details chat() {
        return listing.chatView(tr);
    }

    @Override
    public Subscription onChange(Runnable listener) {
        return changes.onChange(listener);
    }

    private CompletableFuture<Result<String>> byId(
            String id, Function<PlayerId, CompletableFuture<Result<String>>> work) {
        return PlayerDirectory.parse(id)
                .map(work)
                .orElse(PlayerDirectory.completed(Result.failure(PlayerErrors.failed())));
    }
}
