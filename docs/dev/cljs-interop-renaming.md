# JS names the release build renames

The release build (`shadow-cljs release app`) runs Closure's advanced optimizations. They rename every
property name that no extern declares. A method the browser owns then gets another name, and the call
throws `… is not a function`. This happens only in production. The suites run the dev build, which
renames nothing, and they cannot run on a release bundle.

#697: `(.isWellFormed (str value))` became `("" + v).Jl()`, so the example backfill threw on every step
and asked for nothing.

## When a name survives

A property name survives when an extern declares it. There are two sources of externs:

- **Closure's own externs.** They cover the browser and ECMAScript, but they lag behind new
  ECMAScript. `trimEnd`, `at`, `toSorted`, `structuredClone` and `navigator.virtualKeyboard` are
  there. `isWellFormed` and `toWellFormed` (ES2024) are not.
- **Externs shadow-cljs infers** from a `.name` call on a target it takes for a JS object.

Renaming works on the whole build, not on one call. One extern for a name keeps that name everywhere.
That cuts the other way too: deleting the only `^js` call of a name can break its other calls.

Which forms shadow-cljs makes an extern for, measured on a release build (#699):

| Form | Name kept |
|---|---|
| `^js` parameter: `(fn [^js x] (.m x))` | yes |
| `^js` local: `(let [^js s (str x)] (.m s))`, `{:keys [^js x]}` | yes |
| `(js-invoke x "m")`, `(aget x "m")` | yes: a string is never renamed |
| `js/m`, `(.m js/document)`, `(.-m js/navigator)` | yes |
| a call on what an interop call returned: `(.m (.n ^js x))` | yes |
| untyped target: `(.m x)` | **no**; shadow warns `Cannot infer target type` |
| a target of an inferred type: `(str …)`, `"literal"`, a number, `#js [...]`, `^string` | **no, and nothing warns** |
| `^js` on the form itself: `(.m ^js (str x))` | **no, and nothing warns**: the inferred `string` wins |

## The rule

To call a JS method or read a JS property that Closure's externs may lack, put the target in a `^js`
parameter or local, or use `js-invoke`. A `^js` on the call form is not enough when the form has an
inferred type.

`bb scripts/release_renames.clj` enforces it, and CI runs it (`Client Tests`). It builds the release
bundle and reads the map Closure writes of every property it renamed,
`.shadow-cljs/builds/app/release/closure.property.map` (`isWellFormed:Jl`, a line per name). It fails
on every interop name of the bundle's sources that the map holds. The sources are the build's own: the
inputs its `manifest.edn` lists that lie under a `:source-paths` entry of `shadow-cljs.edn`. clj-kondo's analysis (`:instance-invocations`) finds the names in every interop form:
`(.m x)`, `(.-f x)`, `(. x m)`, `(. x -f)`, the members of `(.. x -a (b))`, threaded ones.

## Where the lists are

- Closure's own externs: `externs.zip` in the `closure-compiler` jar the build uses
  (`npx shadow-cljs classpath`).
- The externs shadow-cljs inferred for this build: `.shadow-cljs/builds/app/release/externs.shadow.js`.
- What was renamed: `.shadow-cljs/builds/app/release/closure.property.map`, written by every release
  build (not by one with `--pseudo-names`).

No file lists which forms make shadow-cljs infer an extern. The table above is measured.
