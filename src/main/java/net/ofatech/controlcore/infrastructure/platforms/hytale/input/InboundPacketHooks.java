package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.io.handlers.GenericPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class InboundPacketHooks {

    static boolean watched(Packet packet) {
        return packet instanceof SyncInteractionChains
                || packet instanceof ClientMovement
                || packet instanceof ChatMessage;
    }

    private final Log log;
    private final List<PlayerPacketFilter> filters = new CopyOnWriteArrayList<>();
    private final Set<PacketHandler> hooked =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private boolean subHandlerRegistered;
    private PacketFilter fallback;
    private boolean nativeLookupFailed;

    public InboundPacketHooks(Log log) {
        this.log = log;
    }

    synchronized void add(PlayerPacketFilter filter) {
        if (!filters.contains(filter)) {
            filters.add(filter);
        }
        if (!subHandlerRegistered) {
            subHandlerRegistered = true;
            ServerManager.get().registerSubPacketHandlers(this::hooksFor);
        }
        if (fallback == null) {
            fallback = this::fallback;
            PacketAdapters.registerInbound(fallback);
        }
    }

    synchronized void remove(PlayerPacketFilter filter) {
        filters.remove(filter);
        if (filters.isEmpty() && fallback != null) {
            PacketAdapters.deregisterInbound(fallback);
            fallback = null;
        }
    }

    private SubPacketHandler hooksFor(IPacketHandler handler) {
        return () -> {
            if (handler instanceof GamePacketHandler game) {
                hook(game, SyncInteractionChains.PACKET_ID, packet -> game.handle((SyncInteractionChains) packet));
                hook(game, ClientMovement.PACKET_ID, packet -> game.handle((ClientMovement) packet));
                hook(game, ChatMessage.PACKET_ID, packet -> game.handle((ChatMessage) packet));
                hooked.add(game);
            }
        };
    }

    private void hook(GamePacketHandler game, int packetId, Consumer<ToServerPacket> builtIn) {
        Consumer<ToServerPacket> next = current(game, packetId).orElse(builtIn);
        game.registerHandler(packetId, packet -> {
            if (!filtered(game.getPlayerRef(), packet)) {
                next.accept(packet);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Optional<Consumer<ToServerPacket>> current(GamePacketHandler game, int packetId) {
        if (nativeLookupFailed) {
            return Optional.empty();
        }
        try {
            Field field = GenericPacketHandler.class.getDeclaredField("handlers");
            field.setAccessible(true);
            Consumer<ToServerPacket>[] handlers = (Consumer<ToServerPacket>[]) field.get(game);
            return handlers != null && packetId < handlers.length
                    ? Optional.ofNullable(handlers[packetId])
                    : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            nativeLookupFailed = true;
            log.warn("Could not read Hytale's packet handlers, using its public handlers instead: " + e);
            return Optional.empty();
        }
    }

    private boolean fallback(PacketHandler handler, Packet packet) {
        if (!watched(packet) || hooked.contains(handler) || !(handler instanceof GamePacketHandler game)) {
            return false;
        }
        return filtered(game.getPlayerRef(), packet);
    }

    private boolean filtered(PlayerRef player, Packet packet) {
        if (player == null) {
            return false;
        }
        boolean blocked = false;
        for (PlayerPacketFilter filter : filters) {
            try {
                blocked |= filter.test(player, packet);
            } catch (RuntimeException e) {
                log.error("Could not read the input of " + player.getUsername(), e);
            }
        }
        return blocked;
    }
}
