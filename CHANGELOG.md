# Changelog

## Unreleased

### Changed
- Rebuilt on the OFA-Tech Hytale mod template: `com.azuredoom.hytale-tools` 1.0.51 instead of
  ScaffoldIt, Gradle 9.8.1, Java 25, Hytale server 0.7.2 (`>=0.7.2 <0.8.0`).
- Plugin id is now `OFATech:ControlCore` (was `net.ofatech:ControlCore`); entry point
  `net.ofatech.controlcore.Main`.
- Code reorganised into `api` / `domain` / `application` / `infrastructure`. Hytale is only used in
  `infrastructure/platforms/hytale`, HyUI only in `infrastructure/platforms/hyui`.
- HyUI is no longer bundled: it is an optional dependency installed next to ControlCore.
- `/open-admin-config` now requires the `<base permission>.admin` node.

### Added
- `ControlCoreApi` / `ControlPanelApi` for other mods; `Result` / `Error` for expected failures.
- `Settings.json` with `ControlPanelEnabled`.
- ArchUnit architecture tests, runtime and feature tests with fakes, Spotless, build workflow.

### Removed
- Welcome message on join (`ExampleEvent`), avaje-inject, the reflective `ModComponentScanner`,
  unused `*Extensions` helpers and the unused `config/TemplatePlugin/config.json`.
