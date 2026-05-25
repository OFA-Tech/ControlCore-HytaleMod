# Dependency Injection System

ControlCore includes a built-in dependency injection container inspired by C#'s `ServiceCollection` pattern. This system supports three service lifetimes:

## Service Lifetimes

### 1. **Singleton**
A single instance is created for the entire application lifecycle. The same instance is reused every time the service is requested.

```java
services.addSingleton(IMyService.class, MyService.class);
// or with instance
services.addSingleton(IMyService.class, new MyService());
// or with factory
services.addSingleton(IMyService.class, provider -> new MyService());
```

**Use cases:** Configuration services, logging, UI panels, database connections

### 2. **Scoped**
A single instance is created per scope. In this implementation, there is one application-wide scope, so scoped services behave similarly to singletons but are conceptually meant for per-request or per-operation scenarios.

```java
services.addScoped(IMyService.class, MyService.class);
// or with factory
services.addScoped(IMyService.class, provider -> new MyService());
```

**Use cases:** Unit of work patterns, per-operation state

### 3. **Transient**
A new instance is created every time the service is requested.

```java
services.addTransient(IMyService.class, MyService.class);
// or with factory
services.addTransient(IMyService.class, provider -> new MyService());
```

**Use cases:** Stateless utilities, command handlers, temporary objects

## Usage

### Setting up the Service Collection

In `ControlCore.java`, the `configureDependencies()` method is called during plugin setup:

```java
private void configureDependencies() {
    IServiceCollection services = new ServiceCollection();

    // Register your services here
    services.addSingleton(ControlPanel.class, new ControlPanel());
    services.addSingleton(IConfigService.class, ConfigService.class);
    services.addTransient(ICommandHandler.class, CommandHandler.class);

    // Build the provider
    serviceProvider = services.buildServiceProvider();
}
```

### Resolving Services

Services can be resolved in two ways:

#### 1. Using `getService()` (Optional)
```java
Optional<ControlPanel> panel = plugin.getServiceProvider()
    .getService(ControlPanel.class);

if (panel.isPresent()) {
    panel.get().openControlPanel(player, request);
}
```

#### 2. Using `getRequiredService()` (Mandatory)
Throws an `IllegalArgumentException` if the service is not registered.

```java
ControlPanel panel = plugin.getServiceProvider()
    .getRequiredService(ControlPanel.class);
```

## Example: Using DI in Commands

### Before (Direct Instantiation)
```java
public class MyCommand extends AbstractCommand implements IModCommand {
    private MyService service;

    public MyCommand(MyService service) {
        super("mycommand", "My command");
        this.service = service;
    }

    @Override
    public void register(ControlCore plugin) {
        plugin.getCommandRegistry().registerCommand(this);
    }
}

// Problem: Requires constructor parameter, breaks auto-registration
```

### After (Dependency Injection)
```java
public class MyCommand extends AbstractCommand implements IModCommand {
    private MyService service;

    public MyCommand() {
        super("mycommand", "My command");
    }

    @Override
    public void register(ControlCore plugin) {
        // Get service from DI container
        service = plugin.getServiceProvider()
            .getRequiredService(MyService.class);

        plugin.getCommandRegistry().registerCommand(this);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        service.doSomething();
        return CompletableFuture.completedFuture(null);
    }
}

// And register in configureDependencies():
// services.addSingleton(MyService.class, MyService.class);
```

## Example: Nested Dependencies

The service provider supports nested dependency resolution. A service can request other services during creation:

```java
// Define services
public interface IDatabase {
    void connect();
}

public interface IConfigService {
    Config getConfig();
}

public class Database implements IDatabase {
    public Database() { }
    public void connect() { }
}

public class ConfigService implements IConfigService {
    private IDatabase database;

    public ConfigService(IDatabase database) {
        this.database = database;
    }

    public Config getConfig() {
        database.connect();
        return new Config();
    }
}

// Registration with factory that resolves dependencies
services.addSingleton(IDatabase.class, Database.class);
services.addSingleton(IConfigService.class,
    provider -> new ConfigService(
        provider.getRequiredService(IDatabase.class)
    )
);
```

## Best Practices

1. **Use Interfaces**: Register and inject interfaces, not concrete classes
   ```java
   // Good
   services.addSingleton(IMyService.class, MyService.class);

   // Avoid
   services.addSingleton(MyService.class, MyService.class);
   ```

2. **Choose the Right Lifetime**:
   - `Singleton` for stateless utilities and shared resources
   - `Scoped` for per-operation state
   - `Transient` for stateless commands and handlers

3. **Lazy Initialize in Commands**: Resolve dependencies in the `register()` method
   ```java
   @Override
   public void register(ControlCore plugin) {
       myService = plugin.getServiceProvider()
           .getRequiredService(MyService.class);
       plugin.getCommandRegistry().registerCommand(this);
   }
   ```

4. **Factory Functions**: Use factories for complex initialization
   ```java
   services.addSingleton(IComplexService.class,
       provider -> {
           IDatabase db = provider.getRequiredService(IDatabase.class);
           return new ComplexService(db, config, logger);
       }
   );
   ```

## Architecture

### Key Components

- **`IServiceCollection`**: Interface for registering services
- **`ServiceCollection`**: Default implementation of IServiceCollection
- **`ServiceDescriptor`**: Describes a service registration (type, factory, lifetime)
- **`IServiceProvider`**: Interface for resolving services
- **`ServiceProvider`**: Default implementation of IServiceProvider
- **`ServiceLifetime`**: Enum for service lifetimes (SINGLETON, SCOPED, TRANSIENT)
- **`ServiceFactory<T>`**: Functional interface for creating service instances

### Flow

1. Services are registered with the `ServiceCollection`
2. `buildServiceProvider()` creates a `ServiceProvider` from the descriptors
3. Commands/events resolve services from the provider during registration
4. Services are cached according to their lifetime


