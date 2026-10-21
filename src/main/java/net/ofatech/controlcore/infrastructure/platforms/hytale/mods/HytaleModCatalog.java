package net.ofatech.controlcore.infrastructure.platforms.hytale.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.config.ModConfig;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginState;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.ofatech.controlcore.domain.model.mods.ModInfo;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.mods.ModCatalog;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;
import net.ofatech.controlcore.infrastructure.platforms.hytale.server.HytaleServerConfiguration;

public final class HytaleModCatalog implements ModCatalog {

    private static final int MAX_FILES = 5;
    private static final int MAX_LINES = 60;
    private static final long MAX_FILE_BYTES = 256 * 1024;

    private final HytaleServerConfiguration serverConfig;
    private final Log log;

    public HytaleModCatalog(HytaleServerConfiguration serverConfig, Log log) {
        this.serverConfig = serverConfig;
        this.log = log;
    }

    @Override
    public List<ModInfo> mods() {
        PluginManager plugins = PluginManager.get();
        Map<String, PluginBase> loaded = new HashMap<>();
        for (PluginBase plugin : plugins.getPlugins()) {
            loaded.put(plugin.getIdentifier().toString(), plugin);
        }
        Map<String, AssetPack> packs = new HashMap<>();
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            packs.put(pack.getName(), pack);
        }
        Map<String, PluginManifest> manifests = new LinkedHashMap<>();
        plugins.getAvailablePlugins().forEach((id, manifest) -> manifests.put(id.toString(), manifest));
        loaded.forEach((id, plugin) -> manifests.putIfAbsent(id, plugin.getManifest()));

        HytaleServerConfig config = HytaleServer.get().getConfig();
        List<ModInfo> mods = new ArrayList<>();
        manifests.forEach((id, manifest) -> {
            PluginBase plugin = loaded.get(id);
            ModInfo.Kind kind = packs.containsKey(id) ? ModInfo.Kind.PLUGIN_WITH_ASSETS : ModInfo.Kind.PLUGIN;
            mods.add(info(id, manifest, kind, state(plugin), config));
        });
        packs.forEach((id, pack) -> {
            if (!manifests.containsKey(id) && pack.getManifest() != null) {
                mods.add(info(id, pack.getManifest(), ModInfo.Kind.ASSET_PACK, ModInfo.State.ENABLED, config));
            }
        });
        return mods;
    }

    @Override
    public Result<ModInfo> setEnabled(String modId, boolean enabled) {
        PluginIdentifier id;
        try {
            id = PluginIdentifier.fromString(modId);
        } catch (RuntimeException e) {
            return unknown(modId);
        }
        HytaleServerConfig config = HytaleServer.get().getConfig();
        Map<PluginIdentifier, ModConfig> modConfig = new HashMap<>(config.getModConfig());
        ModConfig entry = modConfig.computeIfAbsent(id, key -> new ModConfig());
        entry.setEnabled(enabled);
        config.setModConfig(modConfig);
        serverConfig.save(config);
        return mods().stream()
                .filter(mod -> mod.id().equals(id.toString()))
                .findFirst()
                .map(Result::success)
                .orElseGet(() -> unknown(modId));
    }

    @Override
    public List<ConfigFile> configFiles(String modId) {
        PluginBase plugin;
        try {
            plugin = PluginManager.get().getPlugin(PluginIdentifier.fromString(modId));
        } catch (RuntimeException e) {
            return List.of();
        }
        if (plugin == null || plugin.getDataDirectory() == null || !Files.isDirectory(plugin.getDataDirectory())) {
            return List.of();
        }
        List<ConfigFile> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(plugin.getDataDirectory())) {
            for (Path file : listing.filter(
                            path -> path.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .limit(MAX_FILES)
                    .toList()) {
                files.add(read(file));
            }
        } catch (IOException e) {
            log.warn("Could not list the config files of " + modId + ": " + e.getMessage());
        }
        return files;
    }

    private ConfigFile read(Path file) {
        List<String> lines = new ArrayList<>();
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return new ConfigFile(file.getFileName().toString(), List.of("(too large to show)"));
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                flatten("", JsonParser.parseReader(reader), lines);
            }
        } catch (IOException | RuntimeException e) {
            lines.add("(could not read: " + e.getMessage() + ")");
        }
        if (lines.size() > MAX_LINES) {
            int more = lines.size() - MAX_LINES;
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            lines.add("... " + more + " more");
        }
        return new ConfigFile(file.getFileName().toString(), lines);
    }

    private static void flatten(String path, JsonElement element, List<String> lines) {
        if (element.isJsonObject()) {
            if (element.getAsJsonObject().isEmpty()) {
                lines.add((path.isEmpty() ? "(empty)" : path + " = {}"));
            }
            element.getAsJsonObject()
                    .entrySet()
                    .forEach(entry -> flatten(
                            path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), entry.getValue(), lines));
        } else if (element.isJsonArray()
                && element.getAsJsonArray().asList().stream().allMatch(JsonElement::isJsonPrimitive)) {
            lines.add(path + " = " + element);
        } else if (element.isJsonArray()) {
            for (int index = 0; index < element.getAsJsonArray().size(); index++) {
                flatten(path + "[" + index + "]", element.getAsJsonArray().get(index), lines);
            }
        } else {
            String value = element.isJsonNull()
                    ? "null"
                    : element.getAsJsonPrimitive().isString() ? element.getAsString() : element.toString();
            lines.add(path + " = " + (value.length() > 120 ? value.substring(0, 117) + "..." : value));
        }
    }

    private static ModInfo info(
            String id, PluginManifest manifest, ModInfo.Kind kind, ModInfo.State state, HytaleServerConfig config) {
        String group = nullToEmpty(manifest.getGroup());
        String name = nullToEmpty(manifest.getName());
        boolean core = PluginManifest.CORE_GROUP.equalsIgnoreCase(group);
        List<String> authors = new ArrayList<>();
        if (manifest.getAuthors() != null) {
            for (AuthorInfo author : manifest.getAuthors()) {
                if (author != null
                        && author.getName() != null
                        && !author.getName().isBlank()) {
                    authors.add(author.getName());
                }
            }
        }
        String logoFile = group + "_" + name + ".png";
        Optional<String> logo =
                CommonAssetRegistry.hasCommonAsset("UI/Custom/" + logoFile) ? Optional.of(logoFile) : Optional.empty();
        return new ModInfo(
                id,
                name.isEmpty() ? id : name,
                group,
                manifest.getVersion() == null ? "" : manifest.getVersion().toString(),
                manifest.getDescription(),
                authors,
                manifest.getWebsite(),
                kind,
                state,
                core,
                core || enabledAfterRestart(id, manifest, config),
                logo);
    }

    private static boolean enabledAfterRestart(String id, PluginManifest manifest, HytaleServerConfig config) {
        try {
            ModConfig own = config.getModConfig().get(PluginIdentifier.fromString(id));
            if (own != null && own.getEnabled() != null) {
                return own.getEnabled();
            }
        } catch (RuntimeException unparsableModId) {
        }
        return config.getDefaultModsEnabled() && !manifest.isDisabledByDefault();
    }

    private static ModInfo.State state(PluginBase plugin) {
        if (plugin == null) {
            return ModInfo.State.NOT_LOADED;
        }
        PluginState state = plugin.getState();
        if (state == PluginState.FAILED) {
            return ModInfo.State.FAILED;
        }
        return state.isInactive() ? ModInfo.State.DISABLED : ModInfo.State.ENABLED;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static <T> Result<T> unknown(String modId) {
        return Result.failure(Error.notFound(
                "Mods.Unknown",
                Text.key("controlcore.errors.mods.unknown", "There is no mod {id}.")
                        .with("id", modId)));
    }
}
