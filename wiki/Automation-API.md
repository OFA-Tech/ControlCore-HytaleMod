# Automation API (API 2.1.0)

Four building blocks for mods, available from `ControlCoreApi.get()` (or `find()`).

## Notifications

```java
api.notifications().message(player, "Hello");
api.notifications().message(player, "Careful!", "#ff5555");
api.notifications().broadcast("Restart in 5 minutes");
api.notifications().toAdmins("Backup finished");
api.notifications().title(player, "Welcome!", "Have fun");
api.notifications().titleAll("Event", "starts now");

// API 2.2.0: toasts and warning bars (players choose where they appear)
api.notifications().toast(player, "Saved", "Your home was set", ToastLevel.SUCCESS, 6);
api.notifications().toastAll("", "Backup finished", ToastLevel.INFO, 6);
api.notifications().warningAll("restart", "Restart in 10 minutes", ToastLevel.WARNING, 600);
api.notifications().clearWarningAll("restart");
```

## Scheduler

```java
SchedulerApi.Task task = api.scheduler().every(Duration.ofMinutes(30), () -> ...);
api.scheduler().later(Duration.ofSeconds(10), () -> ...);
api.scheduler().dailyAt(LocalTime.of(20, 0), () -> ...);
task.cancel();
```

Work runs on a background thread; hop to a world's thread before touching that world. A failing run is logged and
later runs continue.

## Sessions

```java
api.sessions().find(player).ifPresent(session -> {
    Duration total = session.playtime();      // including the current session
    Instant first = session.firstJoin();
    boolean online = session.online();
});
api.sessions().world(player);                 // the world the player is in
```

## Actions and triggers for rules

Offer an action admins can pick in the Automation tab ("Mod action" with the value `homes:reset-homes`):

```java
api.actions().register(AutomationAction.of("homes", "reset-homes")
        .displayName("Reset everyone's homes")
        .run(context -> homes.resetAll()));
```

`ActionContext` holds the rule's name, the player (if the trigger had one), the text the admin typed after the id
(with placeholders filled in) and the trigger's placeholders.

Announce your own trigger ("A mod's trigger" with the value `arena:match-won`) and fire it:

```java
api.actions().registerTrigger("arena", "match-won", "An arena match is won");
api.actions().fire("arena", "match-won", Optional.of(winner), Map.of("arena", "Lava pit"));
```

Rules can then use `{arena}`. `/cc-rules` lists every registered action and trigger.
