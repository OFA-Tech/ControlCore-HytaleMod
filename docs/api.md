# Public API

Other mods use ControlCore through the interfaces in `net.ofatech.controlcore.api`. The services
implement them directly; there are no copies or adapters. Signatures use plain Java and `domain`
types (`Result`, `Error`, `PlayerId`, `PlayerInfo`, `ControlPanelRequest`, events), never Hytale
types, so the API survives Hytale updates.

| Type | Purpose |
|---|---|
| `ControlCoreApi` | entry point: `get()`, `find()`, `apiVersion()`, `controlPanel()`, `events()` |
| `ControlPanelApi` | `isAvailable()`, `open(player, request)` |
| `ControlCoreEvents` | `subscribe(eventType, listener)` returning a closable `Subscription` |

`ControlPanelApi.open` does not check permissions; the calling mod decides who may open the panel. It
returns errors such as `ControlPanel.Disabled` and `ControlPanel.PlayerNotFound`.
Translated game events (`PlayerJoined`, `PlayerLeft`)
are delivered on the events bus.

## Using it from another mod

1. **Dependency.** Until ControlCore is published, run `./gradlew publishToMavenLocal` here and add
   `mavenLocal()` to the other mod's repositories. Once releases are published to GitHub Packages
   (needs a token with `read:packages`):

   ```kotlin
   repositories {
       maven {
           url = uri("https://maven.pkg.github.com/OFA-Tech/ControlCore-HytaleMod")
           credentials {
               username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
               password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
           }
       }
   }
   dependencies {
       compileOnly("net.ofatech:controlcore:0.1.0")
       vineMod("net.ofatech:controlcore:0.1.0") // run it in your dev server
   }
   ```
2. **Manifest.** Use `manifest_dependencies = OFATech:ControlCore=>=0.1.0`, or
   `manifest_opt_dependencies` if your mod should work without it.
3. **Code.**

   ```java
   ControlCoreApi.find().ifPresent(api -> {
       if (api.controlPanel().isAvailable()) {
           api.controlPanel().open(playerId, ControlPanelRequest.ALL);
       }
   });
   ```

   `get()` throws if ControlCore is not loaded or not set up yet; `find()` returns empty instead. With an
   optional dependency, keep calls to our classes in a separate class that you only touch after
   `PluginManager.get().getPlugin(PluginIdentifier.fromString("OFATech:ControlCore"))` is non-null.

## Rules for maintainers

- Only interfaces and the provider live in `api`. Add a method to the interface and implement it in
  the service; never copy models.
- Everything public in `api` (and the `domain` types it exposes) has Javadoc.
- Semantic versioning on `ControlCoreApi.VERSION` and the mod version. Removing or changing a method
  is a major version bump; adding one is minor.
- The API is published at the end of `setup()` and withdrawn at the start of `shutdown()`.

## Publishing

`maven-publish` publishes `net.ofatech:controlcore` (the plugin jar plus sources and javadoc). The
artifact id is `mod_id` in lower case, the repository is `github_repository` in `gradle.properties`.

`.github/workflows/release.yml` is commented out for now. Once uncommented, a `vX.Y.Z` tag runs
`./gradlew publish` to GitHub Packages and attaches the jars to a GitHub Release.
