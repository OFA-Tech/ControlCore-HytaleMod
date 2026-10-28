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
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.ports.system.Log;

final class InboundPacketHooks implements SubPacketHandler {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final List<PlayerPacketFilter> FILTERS = new CopyOnWriteArrayList<>();
    private static final Set<PacketHandler> HOOKED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final java.util.concurrent.atomic.AtomicReference<Log> LOG =
            new java.util.concurrent.atomic.AtomicReference<>();

    private final IPacketHandler packetHandler;

    private InboundPacketHooks(IPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    static void add(PlayerPacketFilter filter, Log logger) {
        LOG.set(logger);
        if (!FILTERS.contains(filter)) {
            FILTERS.add(filter);
        }
        if (INSTALLED.compareAndSet(false, true)) {
            ServerManager.get().registerSubPacketHandlers(InboundPacketHooks::new);
            PacketAdapters.registerInbound((PacketFilter) InboundPacketHooks::fallback);
        }
    }

    static void remove(PlayerPacketFilter filter) {
        FILTERS.remove(filter);
    }

    @Override
    public void registerHandlers() {
        if (!(packetHandler instanceof GamePacketHandler game)) {
            return;
        }
        hook(game, SyncInteractionChains.PACKET_ID, packet -> game.handle((SyncInteractionChains) packet));
        hook(game, ClientMovement.PACKET_ID, packet -> game.handle((ClientMovement) packet));
        hook(game, ChatMessage.PACKET_ID, packet -> game.handle((ChatMessage) packet));
        HOOKED.add(game);
    }

    private static void hook(GamePacketHandler game, int packetId, Consumer<ToServerPacket> nativeHandler) {
        game.registerHandler(packetId, packet -> {
            if (!filtered(game.getPlayerRef(), packet)) {
                nativeHandler.accept(packet);
            }
        });
    }

    private static boolean fallback(PacketHandler handler, Packet packet) {
        if (HOOKED.contains(handler) || !(handler instanceof GamePacketHandler game)) {
            return false;
        }
        return filtered(game.getPlayerRef(), packet);
    }

    private static boolean filtered(PlayerRef player, Packet packet) {
        if (player == null) {
            return false;
        }
        for (PlayerPacketFilter filter : FILTERS) {
            try {
                if (filter.test(player, packet)) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log current = LOG.get();
                if (current != null) {
                    current.error("Could not read input of " + player.getUsername(), e);
                }
            }
        }
        return false;
    }
}
