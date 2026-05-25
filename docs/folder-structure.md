# Folder Structure

The default Java package is `net.ofatech.controlcore`.
Below is the core layout you get from the template.

```
src/main/java/net/ofatech/hytaletemplate
├─ TemplatePlugin.java
├─ api
│  ├─ TemplateAPI.java
│  └─ integration
├─ core
│  ├─ data
│  ├─ domain
│  │  ├─ extensions
│  │  ├─ interfaces
│  │  └─ models
│  └─ service
└─ platform
   ├─ command
   ├─ event
   └─ registry
```

- `api` is where public contracts live.
- `core` is the domain layer plus services and shared logic.
- `platform` is where Hytale API integration stays.

