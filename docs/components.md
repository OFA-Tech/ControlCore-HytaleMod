# Components

A component is a class the runtime finds and wires for you. Write the class, annotate it with
`@AutoService(<interface>.class)` and give it one public constructor listing what it needs.

| Interface | What it is | Registered |
|---|---|---|
| `ModCommand` | a chat command: `spec()` + `execute(ctx)` | `setup()` |
| `ModEventHandler<E>` | reacts to one event type | `setup()` |
| `FeatureModule` | a feature with `setup` / `start` / `shutdown` hooks | `setup()`, started in `start()` |
| `ModTask` | delayed or repeating work | scheduled in `start()` |
| `ModPlatform` | descriptor of another mod (see [platforms.md](platforms.md)) | bound in `start()` |

All of them live in `application/<feature>/` except `ModPlatform`, which lives in
`infrastructure/platforms/<mod>/`.

## Injection

Constructor parameters are resolved by type:

- **Ports** (`Players`, `Messenger`, `Permissions`, `Scheduler`, `PlayerData`, `SettingsProvider`, `SettingStore`,
  `CommandRegistrar`, `ModLookup`, `Clock`, `Log`), bound by the platform.
- **Runtime objects**: `EventPublisher`, `ControlCoreEvents`, `Capabilities`, `ModuleContext`.
- **Your own classes**, created once and shared, for example `ControlPanelService`.
- **Interfaces with one `@AutoService` provider**, for example `ControlPanelApi` → `ControlPanelService`
  and `SettingsApi` → `SettingsRegistry`.

Generic parameters (`Optional<X>`, `List<X>`) are not supported. Ask `Capabilities` for optional
things instead.

## Commands

```java
@AutoService(ModCommand.class)
public final class HealCommand implements ModCommand {

    private final Players players;

    public HealCommand(Players players) {
        this.players = players;
    }

    @Override
    public CommandSpec spec() {
        return CommandSpec.named("heal")
                .description("Heal a player")
                .arg("target", ArgType.PLAYER, "Who to heal")
                .optionalArg("amount", ArgType.INTEGER, "How much")
                .permission("heal")          // becomes <base permission>.heal
                .aliases("h");
    }

    @Override
    public Result<Reply> execute(CommandContext context) {
        PlayerInfo target = context.player("target");
        int amount = context.arg("amount").map(Integer.class::cast).orElse(20);
        return Result.success(Reply.of("Healed " + target.name() + " by " + amount));
    }
}
```

- Argument types: `STRING`, `GREEDY_STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `PLAYER`.
- Without `.permission(...)` everyone can run the command. `.requirePlayer()` rejects the console.
- Return `Result.failure(Error.notFound(...))` and the player gets the description in red.
- Exceptions are caught, logged, and answered with a generic message.

## Event handlers

```java
@SuppressWarnings("rawtypes") // AutoService cannot name generic interfaces exactly
@AutoService(ModEventHandler.class)
public final class SayBye implements ModEventHandler<PlayerLeft> {
    public SayBye(Messenger messenger) { ... }
    @Override public Class<PlayerLeft> eventType() { return PlayerLeft.class; }
    @Override public void handle(PlayerLeft event) { ... }
}
```

Events available today: `PlayerJoined`, `PlayerLeft` (from Hytale) and `SettingChanged` (from the
settings registry). To get a new Hytale event, translate it in
`platforms/hytale/event/HytaleGameEvents` (see [platforms.md](platforms.md)).

## Feature modules

```java
@AutoService(FeatureModule.class)
public final class ShopModule implements FeatureModule {
    @Override public boolean isEnabled(ModuleContext ctx) { return true; }
    @Override public void setup(ModuleContext ctx) { /* register dynamic commands, subscribe */ }
    @Override public void start(ModuleContext ctx) { ctx.capabilities().find(ControlPanelView.class)... }
    @Override public void shutdown() { }
}
```

Use a module when a feature needs lifecycle hooks or depends on another mod's capability (only
available from `start()` on).

## Tasks

```java
@AutoService(ModTask.class)
public final class Autosave implements ModTask {
    @Override public TaskSchedule schedule() { return TaskSchedule.every(Duration.ofMinutes(5)); }
    @Override public void run() { ... }
    @Override public boolean runOnShutdown() { return true; }
}
```

Tasks run off the world thread. Do not touch world or entity state directly from a task.

## Settings

ControlCore's own features use the same registry as other mods. Put the settings of a feature in a small
class that registers them in its constructor and exposes typed getters, then inject that class:

```java
public final class ControlPanelSettings {
    private final Setting<Boolean> enabled;

    public ControlPanelSettings(SettingsApi settings, SettingsProvider defaults) {
        this.enabled = settings.register(SettingDefinition.server(
                        "controlcore", "control-panel-enabled", SettingType.bool(),
                        defaults.current().controlPanelEnabled())
                .named("Control panel enabled")).value();
    }

    public boolean panelEnabled() { return enabled.get(); }
}
```

Read the value on every use (`enabled.get()`), so `/cc-set` takes effect without a restart. Values in
`Settings.json` only serve as defaults for these settings. See [api.md](api.md#settings-registry) for
types, scopes and commands.

## Testing

Build the class with fakes from `src/test/java/.../fakes`, or run the whole runtime with
`FakePlatform` as `ModRuntimeTest` does. No server is needed.
