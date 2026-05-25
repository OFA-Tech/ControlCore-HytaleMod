# Architecture

## Overview

The template keeps the plugin entrypoint thin and pushes behavior into feature classes that are discovered at startup.
The `TemplatePlugin` class scans the plugin package using `ModComponentScanner` and registers any concrete classes that implement `IModCommand` or `IModEvent`.
This keeps registration consistent while avoiding a large manual registry.

## Module boundaries

- `api` is the public surface area you want other mods to consume.
- `core` holds domain models, services, and cross-cutting logic that should stay Hytale-agnostic.
- `platform` contains Hytale-specific implementations (commands, events, adapters).

## Registration flow

1. `TemplatePlugin.setup()` calls `registerDynamicComponents()`.
2. `ModComponentScanner` searches the plugin package for class files.
3. Any concrete `IModCommand` or `IModEvent` classes are instantiated via no-arg constructors.
4. Each instance calls its `register` method to attach itself to the Hytale registries.

Because reflection is used, command and event classes must have no-argument constructors and must live under the plugin package.

