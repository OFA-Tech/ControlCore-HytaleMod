# Hytale Java Plugin Architecture Template

This repository is a **cloneable Hytale Java plugin architecture template**.
It is intended to be copied and renamed for new plugin/mod projects.

## Project status

This template is stable and intentionally minimal.
There is no planned feature roadmap right now, but issues and PRs are welcome.

## What this template is

- A starter project with a **DDD-inspired folder structure** adapted for Java/Hytale.
- A template that keeps the plugin entrypoint thin and separates concerns.
- A reference for where to place public API contracts, domain code, services, and platform integration.

## What this template is not

- Not a shared runtime framework dependency.
- Not a global cross-mod API registry.
- Not a ServiceLoader-based integration framework.

## Quick start

- Update metadata in `gradle.properties` (name, group, description, version, authors).
- Rename `TemplatePlugin` and its package, then update `plugin_main` in `gradle.properties`.
- Remove or replace the example command/event classes once you have your own.
- Follow the [Rename Guide](docs/rename-guide.md) for a step-by-step checklist.

## Architecture summary

- `TemplatePlugin` is the entrypoint and should stay thin.
- `platform` contains Hytale-specific code (commands, events, adapters).
- `core` contains domain, service, and infrastructure internals.
- `api` is a placeholder for the public API surface of the mod you build from this template.

## Documentation

- [Architecture](docs/architecture.md)
- [Folder Structure](docs/folder-structure.md)
- [Rename Guide](docs/rename-guide.md)
- [API Folder Guide](docs/api-folder.md)
- [Examples](docs/examples.md)
