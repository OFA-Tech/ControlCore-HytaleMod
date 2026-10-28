package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.InputProbe;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

public final class HytaleInputProbe implements InputProbe, PlayerPacketWatcher {

    private static final long REPEAT_MILLIS = 1_000;

    private final Log log;
    private final Set<UUID> probing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> lastMovement = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOther = new ConcurrentHashMap<>();
    private final Set<String> seenChains = ConcurrentHashMap.newKeySet();
    private volatile boolean registered;

    public HytaleInputProbe(Log log) {
        this.log = log;
    }

    @Override
    public synchronized boolean toggle(PlayerId player) {
        if (!registered) {
            InboundPacketHooks.add(
                    (sender, packet) -> {
                        accept(sender, packet);
                        return false;
                    },
                    log);
            PacketAdapters.registerInbound((PlayerPacketWatcher) (sender, packet) -> {
                if (!(packet instanceof SyncInteractionChains
                        || packet instanceof ClientMovement
                        || packet instanceof ChatMessage)) {
                    accept(sender, packet);
                }
            });
            registered = true;
        }
        UUID id = player.value();
        if (probing.remove(id)) {
            lastMovement.remove(id);
            seenChains.removeIf(key -> key.startsWith(id + ":"));
            return false;
        }
        probing.add(id);
        return true;
    }

    @Override
    public boolean isOn(PlayerId player) {
        return probing.contains(player.value());
    }

    @Override
    public Result<Void> openKeyTest(PlayerId player, String target) {
        KeyProbePage.Target where;
        try {
            where = KeyProbePage.Target.valueOf(target.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.failure(Error.validation(
                    "InputProbe.UnknownTarget",
                    "Unknown target '" + target + "'. Use one of: " + KEY_TEST_TARGETS + "."));
        }
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> entity = playerRef != null ? playerRef.getReference() : null;
        if (entity == null || !entity.isValid()) {
            return Result.failure(Error.notFound("InputProbe.PlayerNotFound", "That player is not in a world."));
        }
        Store<EntityStore> store = entity.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            try {
                Player component = store.getComponent(entity, Player.getComponentType());
                if (component != null && entity.isValid()) {
                    component.getPageManager().openCustomPage(entity, store, new KeyProbePage(playerRef, where, log));
                }
            } catch (RuntimeException e) {
                log.error("Could not open the key test for " + playerRef.getUsername(), e);
            }
        });
        return Result.success();
    }

    @Override
    public void accept(PlayerRef player, Packet packet) {
        if (player == null || !probing.contains(player.getUuid())) {
            return;
        }
        try {
            describe(player, packet).forEach(line -> show(player, line));
        } catch (RuntimeException e) {
            log.debug("Input probe could not describe " + packet.getClass().getSimpleName() + ": " + e);
        }
    }

    private List<String> describe(PlayerRef player, Packet packet) {
        List<String> lines = new ArrayList<>();
        switch (packet) {
            case SyncInteractionChains chains -> {
                if (chains.updates != null) {
                    for (SyncInteractionChain chain : chains.updates) {
                        String key = player.getUuid() + ":" + chain.interactionType + ":" + chain.chainId;
                        if (chain.initial || seenChains.add(key)) {
                            seenChains.add(key);
                            lines.add(chain(chain));
                        }
                    }
                }
            }
            case MouseInteraction mouse -> {
                if (mouse.mouseButton != null) {
                    lines.add("Mouse " + mouse.mouseButton.mouseButtonType + " " + mouse.mouseButton.state + " (clicks "
                            + mouse.mouseButton.clicks + ", slot " + mouse.activeSlot + ")");
                }
            }
            case ClientOpenWindow window -> lines.add("Open window " + window.type);
            case ChatMessage chat -> lines.add("Chat: " + chat.message);
            case ClientMovement movement -> {
                String states = states(movement.movementStates);
                String previous = lastMovement.put(player.getUuid(), states);
                if (previous != null && !previous.equals(states)) {
                    lines.add("Movement: " + changes(previous, states));
                }
            }
            default -> {
                String name = packet.getClass().getSimpleName();
                long now = System.currentTimeMillis();
                Long last = lastOther.put(player.getUuid() + name, now);
                if (!name.equals("Pong") && (last == null || now - last > REPEAT_MILLIS)) {
                    lines.add("Packet " + name + " (id " + packet.getId() + ")");
                }
            }
        }
        return lines;
    }

    private static String chain(SyncInteractionChain chain) {
        StringBuilder text = new StringBuilder("Interaction ").append(chain.interactionType);
        text.append(" (slot ").append(chain.activeHotbarSlot);
        if (chain.data != null && chain.data.targetSlot >= 0 && chain.data.targetSlot != Integer.MIN_VALUE) {
            text.append(" -> ").append(chain.data.targetSlot);
        }
        if (chain.itemInHandId != null) {
            text.append(", item ").append(chain.itemInHandId);
        }
        text.append(", chain ").append(chain.chainId).append(')');
        return text.toString();
    }

    private static String states(MovementStates states) {
        if (states == null) {
            return "";
        }
        List<String> on = new ArrayList<>();
        if (states.walking) {
            on.add("walking");
        }
        if (states.running) {
            on.add("running");
        }
        if (states.sprinting) {
            on.add("sprinting");
        }
        if (states.crouching) {
            on.add("crouching");
        }
        if (states.jumping) {
            on.add("jumping");
        }
        if (states.sliding) {
            on.add("sliding");
        }
        if (states.rolling) {
            on.add("rolling");
        }
        if (states.flying) {
            on.add("flying");
        }
        if (states.gliding) {
            on.add("gliding");
        }
        return String.join(" ", on);
    }

    private static String changes(String before, String after) {
        List<String> old = List.of(before.isEmpty() ? new String[0] : before.split(" "));
        List<String> now = List.of(after.isEmpty() ? new String[0] : after.split(" "));
        List<String> parts = new ArrayList<>();
        now.stream().filter(state -> !old.contains(state)).forEach(state -> parts.add("+" + state));
        old.stream().filter(state -> !now.contains(state)).forEach(state -> parts.add("-" + state));
        return String.join(" ", parts);
    }

    private void show(PlayerRef player, String line) {
        log.info("[input probe] " + player.getUsername() + ": " + line);
        player.sendMessage(Message.raw("[probe] " + line).color("#9ad0ff"));
    }
}
