package net.ofatech.controlcore.infrastructure.platforms.hytale.input;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ofatech.controlcore.domain.ports.game.EmptyHandInput;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleEmptyHandInput implements EmptyHandInput {

    static final String REPORT = "ControlCore_ReportKey";

    private static final Map<String, Set<InteractionType>> ADDED = new LinkedHashMap<>();

    static {
        Set<InteractionType> noDefaultUse = EnumSet.of(
                InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability4, InteractionType.Dodge);
        Set<InteractionType> empty = EnumSet.copyOf(noDefaultUse);
        empty.add(InteractionType.Secondary);
        ADDED.put("Empty", empty);
        ADDED.put("Block", noDefaultUse);
        ADDED.put("Item", noDefaultUse);
    }

    private final String packKey;
    private final Log log;
    private final Map<String, Map<InteractionType, String>> originals = new HashMap<>();

    public HytaleEmptyHandInput(String packKey, Log log) {
        this.packKey = packKey;
        this.log = log;
    }

    @Override
    public synchronized void reportAllKeys(boolean on) {
        try {
            List<UnarmedInteractions> changed = new ArrayList<>();
            for (Map.Entry<String, Set<InteractionType>> set : ADDED.entrySet()) {
                UnarmedInteractions current = UnarmedInteractions.getAssetMap().getAsset(set.getKey());
                if (current == null) {
                    continue;
                }
                Map<InteractionType, String> base =
                        originals.computeIfAbsent(set.getKey(), key -> copyOf(current.getInteractions()));
                Map<InteractionType, String> next = copyOf(base);
                if (on) {
                    set.getValue().forEach(type -> next.putIfAbsent(type, REPORT));
                }
                if (!next.equals(copyOf(current.getInteractions()))) {
                    changed.add(withInteractions(current, next));
                }
            }
            if (changed.isEmpty()) {
                return;
            }
            AssetStore<String, UnarmedInteractions, DefaultAssetMap<String, UnarmedInteractions>> store =
                    AssetRegistry.getAssetStore(UnarmedInteractions.class);
            var result = store.loadAssets(packKey, changed);
            if (result.hasFailed()) {
                log.warn("Could not change the empty-hand interactions " + result.getFailedToLoadKeys());
                return;
            }
            log.info(
                    on
                            ? "Empty-hand keys on: right click, abilities 1, 2 and 4 and dodge are reported with an empty hand"
                            : "Empty-hand keys off: the game's own unarmed interactions are back");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Could not change the empty-hand interactions", e);
        }
    }

    private static Map<InteractionType, String> copyOf(Map<InteractionType, String> interactions) {
        Map<InteractionType, String> copy = new EnumMap<>(InteractionType.class);
        if (interactions != null) {
            copy.putAll(interactions);
        }
        return copy;
    }

    private static UnarmedInteractions withInteractions(
            UnarmedInteractions source, Map<InteractionType, String> interactions) throws ReflectiveOperationException {
        UnarmedInteractions copy = new UnarmedInteractions();
        for (String name : new String[] {"id", "data"}) {
            Field field = UnarmedInteractions.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(copy, field.get(source));
        }
        Field field = UnarmedInteractions.class.getDeclaredField("interactions");
        field.setAccessible(true);
        field.set(copy, interactions);
        return copy;
    }
}
