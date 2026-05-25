# API Folder Guide

The `api` package is reserved for public, stable contracts that other mods can depend on.
Keep implementation details out of this package so you can evolve internals without breaking consumers.

## Suggested contents

- Interfaces or facades for features you want to expose
- DTOs or value objects that cross module boundaries
- Constants and shared identifiers

`TemplateAPI` is a placeholder entrypoint for your public API.
Replace it with a real API surface once you know what you want to expose.

