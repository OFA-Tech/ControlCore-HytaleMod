# ControlCore (Configured Java Plugin)

ControlCore is the **server control and configuration foundation** for OFA-Tech Hytale projects.

Instead of treating configuration as scattered per-mod implementation detail, this project aims to provide a shared layer for server preferences, administrative controls, configurable actions, and reusable quality-of-life systems.

## Purpose

ControlCore exists to reduce configuration fragmentation across mods.

When each mod uses different settings formats, command patterns, and defaults, server administration becomes harder than it needs to be. ControlCore is designed to establish a consistent control surface so specialized gameplay mods can stay focused on gameplay while reusing a common configuration philosophy.

## Vision

The long-term vision is a common configuration backbone where compatible mods can:

- Register configurable concepts in a predictable way.
- Expose server-facing and player-facing options consistently.
- Reuse shared defaults, actions, and administrative patterns.
- Integrate with other mods without fragile one-off configuration bridges.

## Core concepts

- **Server control as a platform service**: configuration is treated as part of server identity, not isolated side files.
- **Configurable interaction**: commands, actions, preferences, and scheduled behavior remain adjustable.
- **Shared quality-of-life infrastructure**: common mod needs are implemented once and reused.
- **Controlled extensibility**: other mods can depend on the control layer without handing over gameplay ownership.

## Current repository scope

This repository still provides a **cloneable Java plugin architecture base** and is intentionally practical for development:

- `TemplatePlugin` remains the thin plugin entrypoint.
- `platform` contains Hytale-facing integration (commands, events, registries).
- `core` contains internal domain and service logic.
- `api` is where stable public contracts can evolve.

## Quick start

- Verify project metadata in `gradle.properties`.
- Rename `TemplatePlugin` and its package for your module identity.
- Update `plugin_main` and any remaining template identifiers.
- Replace example platform classes with your own behavior.
- Use the [Rename Guide](docs/rename-guide.md) for a full checklist.

## Documentation

- [Configured Java Plugin](docs/configured-java-plugin.md)
- [Architecture](docs/architecture.md)
- [Folder Structure](docs/folder-structure.md)
- [Rename Guide](docs/rename-guide.md)
- [API Folder Guide](docs/api-folder.md)
- [Examples](docs/examples.md)
