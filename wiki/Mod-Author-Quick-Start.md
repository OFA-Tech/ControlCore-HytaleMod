# Mod author quick start

Your mod can register **settings**, **key actions** and **HUD elements** with ControlCore. ControlCore stores them,
shows them in its panel (with search, permissions and translations), lets admins and players change them, and tells
your mod when they change. You write no UI, no config files and no commands.

Current API version: **2.0.0** (`ControlCoreApi.VERSION`), shipped with ControlCore 0.10.0.

## 1. Add the dependency

Releases are on GitHub Packages. GitHub asks for a login even for public packages: create a
[personal access token](https://github.com/settings/tokens) with the `read:packages` scope and put it in
`~/.gradle/gradle.properties`:

```properties
gpr.user=<your GitHub user>
gpr.key=<the token>
```

`build.gradle.kts`:

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
    compileOnly("net.ofatech:controlcore:0.10.0")
    vineImplementation("net.ofatech:controlcore:0.10.0") // load it in your dev server (runServer)
}
```

Use `compileOnly`, never `implementation`: ControlCore must not be copied into your jar, since server owners install
it once in `mods/`. With the [hytale-tools](https://github.com/AzureDoom/Hytale-Gradle-Plugin) plugin,
`vineImplementation` loads it in `runServer` (`vineMod` would put it in a separate class loader your code can't see).

## 2. Declare it in your manifest

Required:

```properties
manifest_dependencies = OFATech:ControlCore=>=0.10.0
```

Or optional, if your mod should also work without ControlCore:

```properties
manifest_opt_dependencies = OFATech:ControlCore=>=0.10.0
```

Either way, Hytale loads ControlCore before your mod.

## 3. Use it

ControlCore publishes its API during its own `setup()`. Use it from your `setup()` or later:

```java
import net.ofatech.controlcore.api.ControlCoreApi;
import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.domain.model.keys.Key;
import net.ofatech.controlcore.domain.model.keys.KeyAction;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;

ControlCoreApi.find().ifPresent(api -> {
    Setting<Integer> maxHomes = api.settings()
            .register(SettingDefinition.server("homes", "max-homes", SettingType.integer(1, 50), 3)
                    .named("Max homes")
                    .describedAs("How many homes each player may set"))
            .value();

    api.keymaps().register(KeyAction.of("homes", "go-home")
            .displayName("Go home")
            .command("home {player}")
            .defaultKeys(Key.CTRL, Key.NUM_3));

    int limit = maxHomes.get();
});
```

- `ControlCoreApi.find()` is empty when ControlCore isn't installed or not set up yet; `ControlCoreApi.get()` throws
  instead.
- **Optional dependency:** keep every use of ControlCore's classes in a separate class, and only touch that class when
  `PluginManager.get().getPlugin(PluginIdentifier.fromString("OFATech:ControlCore"))` is not null. Otherwise your mod
  fails to load without ControlCore.
- Calls that can fail return a `Result`: check `isSuccess()`, or use `value()`, `onFailure(...)`, `orElse(...)`.
  Expected failures never throw.

## Next

- [Settings](Settings-API): types, scopes, categories, permissions, change listeners.
- [Key actions](Key-Actions-API): default keys, commands or code, running as the server.
- [HUD elements](HUD-API): let players turn your HUD on and off.
- [Translations](Translations-API): your names in the player's language.
- [Control panel, events and logos](Panel-Events-And-Logos).

Every public type has Javadoc, which your IDE shows from the sources jar published with each release.

## Testing in your dev server

`./gradlew runServer` with the `vineImplementation` line above loads ControlCore next to your mod. Join, `/op add
<you>` in the console, then `/open-admin-config` shows your settings under your mod's name.
