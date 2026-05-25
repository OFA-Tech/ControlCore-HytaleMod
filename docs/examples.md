# Examples

The template ships with minimal examples to show the registration flow.

## ExampleCommand

`ExampleCommand` extends the Hytale command base class and implements `IModCommand`.
The `register` method registers itself with the plugin command registry.

## ExampleEvent

`ExampleEvent` implements `IModEvent` and registers a global listener for `PlayerReadyEvent`.

## Removing examples

Once you have real commands and events, delete the example classes and keep your own implementations under `platform`.
The automatic scanner will pick them up as long as they are concrete classes with no-argument constructors.

