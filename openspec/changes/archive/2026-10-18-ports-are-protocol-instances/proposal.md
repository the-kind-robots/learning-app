## Why

The client's ports were maps of functions under namespaced keys, and every use case was handed the whole `:capabilities` map, so a use case could reach anything and a test had to rebuild the map's exact shape. A port is now a value that implements Clojure protocols, and a use case takes just the instances it uses (#428).

## What Changes

- **BREAKING** (internal): a port's `start!` returns an instance of its protocols (`World`, `Learner`, `Account`, `Page`, `Navigation`, `Install`, `Dictionary`); the examples generator is the function that asks for one example.
- `:capabilities` holds those instances under `:account :dictionary :examples-generator :install :learner :navigation :page :world`; it exists for the Nexus system and `main` only.
- Use cases take the port instances they use as explicit arguments, not the map.
- Tests supply fake port instances (`reify`) instead of capability-shaped maps.

What each of these must do is stated in the delta specs, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client-runtime-dependencies`: `:capabilities` holds port instances; use cases receive the instances they use; the dictionary port's readiness reading is a protocol method.
- `app-logic-testing`: use-case tests supply fake port instances.
