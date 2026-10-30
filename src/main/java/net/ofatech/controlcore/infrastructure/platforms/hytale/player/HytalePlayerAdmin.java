package net.ofatech.controlcore.infrastructure.platforms.hytale.player;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.connection.PongType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.io.ConnectionRegistry;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerAdmin;
import net.ofatech.controlcore.domain.ports.system.Log;
import org.joml.Vector3d;

public final class HytalePlayerAdmin implements PlayerAdmin {

    private static final double DRIFT_ALLOWED = 1.0;

    private final Log log;

    public HytalePlayerAdmin(Log log) {
        this.log = log;
    }

    @Override
    public OptionalInt ping(PlayerId player) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        if (playerRef == null || playerRef.getPacketHandler() == null) {
            return OptionalInt.empty();
        }
        try {
            PacketHandler.PingInfo info = playerRef.getPacketHandler().getPingInfo(PongType.Raw);
            if (info == null) {
                return OptionalInt.empty();
            }
            double average = info.getPingMetricSet().getAverage(PacketHandler.PingInfo.ONE_SECOND_INDEX);
            if (Double.isNaN(average) || average <= 0) {
                return OptionalInt.empty();
            }
            double millis = average * PacketHandler.PingInfo.TIME_UNIT.toNanos(1) / 1_000_000d;
            return OptionalInt.of((int) Math.round(millis));
        } catch (RuntimeException e) {
            log.warn("Could not read the ping of " + playerRef.getUsername() + ": " + e);
            return OptionalInt.empty();
        }
    }

    @Override
    public boolean kick(PlayerId player, String reason) {
        try {
            return ConnectionRegistry.get().disconnect(player.value(), Message.raw(reason)) > 0;
        } catch (RuntimeException e) {
            log.error("Could not kick " + player, e);
            return false;
        }
    }

    @Override
    public CompletableFuture<Optional<String>> gameMode(PlayerId player) {
        CompletableFuture<Optional<String>> done = new CompletableFuture<>();
        onWorldThread(player, "read the game mode of", done, Optional.empty(), (ref, store) -> {
            Player found = store.getComponent(ref, Player.getComponentType());
            done.complete(Optional.ofNullable(found)
                    .map(Player::getGameMode)
                    .map(mode -> mode == GameMode.Creative ? CREATIVE : ADVENTURE));
        });
        return done;
    }

    @Override
    public CompletableFuture<Boolean> setGameMode(PlayerId player, String mode) {
        GameMode target = CREATIVE.equals(mode) ? GameMode.Creative : GameMode.Adventure;
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        onWorldThread(player, "change the game mode of", done, false, (ref, store) -> {
            Player.setGameMode(ref, target, store);
            done.complete(true);
        });
        return done;
    }

    @Override
    public CompletableFuture<Boolean> teleport(PlayerId player, PlayerId to) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        if (Universe.get().getPlayer(player.value()) == null) {
            done.complete(false);
            return done;
        }
        onWorldThread(to, "read the position of", done, false, (targetRef, targetStore) -> {
            TransformComponent transform = targetStore.getComponent(targetRef, TransformComponent.getComponentType());
            if (transform == null) {
                done.complete(false);
                return;
            }
            World targetWorld = targetStore.getExternalData().getWorld();
            Transform destination = transform.getTransform().clone();
            onWorldThread(player, "teleport", done, false, (moverRef, moverStore) -> {
                moverStore.addComponent(
                        moverRef, Teleport.getComponentType(), Teleport.createForPlayer(targetWorld, destination));
                done.complete(true);
            });
        });
        return done;
    }

    @Override
    public CompletableFuture<Optional<List<Item>>> inventory(PlayerId player) {
        CompletableFuture<Optional<List<Item>>> done = new CompletableFuture<>();
        onWorldThread(player, "read the inventory of", done, Optional.empty(), (ref, store) -> {
            Map<String, Integer> counts = new TreeMap<>();
            InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING)
                    .forEach((slot, stack) -> {
                        if (stack != null && !stack.isEmpty() && stack.getItemId() != null) {
                            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
                        }
                    });
            done.complete(Optional.of(counts.entrySet().stream()
                    .map(entry -> new Item(entry.getKey(), entry.getValue()))
                    .toList()));
        });
        return done;
    }

    @Override
    public CompletableFuture<Optional<Spot>> spot(PlayerId player) {
        CompletableFuture<Optional<Spot>> done = new CompletableFuture<>();
        onWorldThread(player, "read the position of", done, Optional.empty(), (ref, store) -> {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                done.complete(Optional.empty());
                return;
            }
            Vector3d position = transform.getPosition();
            done.complete(Optional.of(
                    new Spot(store.getExternalData().getWorld().getName(), position.x, position.y, position.z)));
        });
        return done;
    }

    @Override
    public CompletableFuture<Boolean> keepAt(PlayerId player, Spot spot) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        onWorldThread(player, "hold in place", done, false, (ref, store) -> {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            World here = store.getExternalData().getWorld();
            if (transform == null) {
                done.complete(false);
                return;
            }
            Vector3d position = transform.getPosition();
            double dx = position.x - spot.x();
            double dy = position.y - spot.y();
            double dz = position.z - spot.z();
            boolean moved =
                    !here.getName().equals(spot.world()) || dx * dx + dy * dy + dz * dz > DRIFT_ALLOWED * DRIFT_ALLOWED;
            if (!moved) {
                done.complete(false);
                return;
            }
            World target = Universe.get().getWorld(spot.world());
            if (target == null) {
                done.complete(false);
                return;
            }
            Transform back = transform.getTransform().clone();
            back.getPosition().set(spot.x(), spot.y(), spot.z());
            store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(target, back));
            done.complete(true);
        });
        return done;
    }

    private <T> void onWorldThread(
            PlayerId player,
            String doing,
            CompletableFuture<T> done,
            T whenMissing,
            BiConsumer<Ref<EntityStore>, Store<EntityStore>> work) {
        PlayerRef playerRef = Universe.get().getPlayer(player.value());
        Ref<EntityStore> ref = playerRef == null ? null : playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            done.complete(whenMissing);
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> {
            try {
                if (!ref.isValid()) {
                    done.complete(whenMissing);
                    return;
                }
                work.accept(ref, store);
            } catch (RuntimeException e) {
                log.error("Could not " + doing + " " + playerRef.getUsername(), e);
                done.complete(whenMissing);
            }
        });
    }
}
