## Why

Every interface string is inline Russian in the views, the dialogs' effect arguments and the server's splash, and the page names no language (#472). Another language would mean editing every view.

## Changes

- The page declares its language. Spec: [interface-language](specs/interface-language/spec.md).
- Views write `[:i18n :key params]`; `render!` interpolates the shell before Replicant renders it. The server's splash goes through the same namespace.
- One dictionary, `src/shared/i18n/ru.edn`, holds every string; the screen reads exactly as before.

## Capabilities

### New Capabilities
- `interface-language`: the page declares the language its interface speaks.

## Decisions

- An m1p dictionary, interpolated between view and render (ADR-0025) — over a lookup function called in each view, which would tie every view to the dictionary.
- Our own walk with m1p's `lookup`, copying only branches that hold text — over `m1p/interpolate`, whose postwalk rebuilds every node: on a word list of 200 rows it cost 12–18 ms a render against 2–7 ms (same page, development build).
- No plural function yet: no interface text depends on a count. The first one adds `[:fn/plural …]` over `Intl.PluralRules`.
- The dictionary is EDN under `src/shared`, read when the code compiles (`shadow.resource/inline` on the client, a macro on the server, whose jar carries no source files) — over a file read at run time.
- Keys are named by what the text says, under the screen or subject that owns it (`:lesson/next`, `:word/add`); text repeated with the same meaning shares one key.
- The install guide stays in English, as it was; translating it is not this change.
