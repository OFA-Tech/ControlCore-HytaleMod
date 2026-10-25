package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class PlayerHeads {

    static final int SLOTS = 24;

    private static final String FOLDER = "Pages/ControlCore/Heads/Head";
    private static final String SOURCE = "https://hyvatar.io/render/";
    private static final Duration KEEP = Duration.ofHours(6);
    private static final Duration RETRY = Duration.ofMinutes(10);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, Picture> PICTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private record Session(Object connection, Map<String, Integer> slots) {}

    private record Picture(Optional<byte[]> png, long until) {}

    private static final class Head extends CommonAsset {

        private final byte[] png;

        Head(int slot, byte[] png) {
            super("UI/Custom/" + texture(slot), png);
            this.png = png;
        }

        @Override
        protected CompletableFuture<byte[]> getBlob0() {
            return CompletableFuture.completedFuture(png);
        }
    }

    private PlayerHeads() {}

    static String texture(int slot) {
        return FOLDER + (slot + 1) + ".png";
    }

    static void forget(UUID viewer) {
        SESSIONS.remove(viewer);
    }

    static void show(PlayerRef viewer, String name, Consumer<String> ready) {
        if (name == null || name.isBlank() || looksLikeUuid(name)) {
            return;
        }
        String key = name.strip().toLowerCase(Locale.ROOT);
        Thread.ofVirtual().name("controlcore-head").start(() -> {
            Optional<byte[]> png = picture(key);
            if (png.isEmpty() || !viewer.isValid()) {
                return;
            }
            Integer slot;
            boolean send;
            Map<String, Integer> slots = slotsOf(viewer);
            synchronized (slots) {
                slot = slots.get(key);
                send = slot == null;
                if (send) {
                    slot = freeSlot(slots);
                    slots.put(key, slot);
                } else {
                    slots.put(key, slots.remove(key));
                }
            }
            if (send) {
                sendTo(viewer, new Head(slot, png.get()));
            }
            ready.accept(texture(slot));
        });
    }

    private static Map<String, Integer> slotsOf(PlayerRef viewer) {
        Object connection = viewer.getPacketHandler();
        return SESSIONS.compute(
                        viewer.getUuid(),
                        (id, session) -> session != null && session.connection() == connection
                                ? session
                                : new Session(connection, new LinkedHashMap<>()))
                .slots();
    }

    private static int freeSlot(Map<String, Integer> slots) {
        if (slots.size() < SLOTS) {
            for (int slot = 0; slot < SLOTS; slot++) {
                if (!slots.containsValue(slot)) {
                    return slot;
                }
            }
        }
        String oldest = slots.keySet().iterator().next();
        return slots.remove(oldest);
    }

    private static Optional<byte[]> picture(String key) {
        long now = System.currentTimeMillis();
        Picture known = PICTURES.get(key);
        if (known != null && known.until() > now) {
            return known.png();
        }
        Optional<byte[]> png = download(key);
        PICTURES.put(key, new Picture(png, now + (png.isPresent() ? KEEP : RETRY).toMillis()));
        return png;
    }

    private static Optional<byte[]> download(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE
                            + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "?size=64"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "image/png")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length < PNG.length) {
                return Optional.empty();
            }
            for (int index = 0; index < PNG.length; index++) {
                if (body[index] != PNG[index]) {
                    return Optional.empty();
                }
            }
            return Optional.of(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void sendTo(PlayerRef viewer, CommonAsset asset) {
        byte[] bytes = asset.getBlob().join();
        byte[][] parts = ArrayUtil.split(bytes, 2_621_440);
        ToClientPacket[] packets = new ToClientPacket[1 + parts.length];
        packets[0] = new AssetInitialize(asset.toPacket(), bytes.length);
        for (int index = 0; index < parts.length; index++) {
            packets[1 + index] = new AssetPart(parts[index]);
        }
        viewer.getPacketHandler().write(packets, new AssetFinalize());
        viewer.getPacketHandler().writeNoCache(new RequestCommonAssetsRebuild());
    }

    private static boolean looksLikeUuid(String text) {
        try {
            UUID.fromString(text.strip());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
