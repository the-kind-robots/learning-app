# Implicit awaits in ^:async

Inside an `^:async` fn, some forms in value position are awaited although no `await` is written. A promise that must stay a promise (passed to `Promise.race`, an abort, `unless-closed`) then arrives already settled.

The results below were measured with shadow-cljs 3.4.12 (what the app builds with) and plain ClojureScript 1.12.145 (`cljs.build.api`).

The same examples as a ClojureScript file, with both compilers' results: `cljs_async_pitfalls.cljs`. It is a reference, on no build's source path.

**Create a promise you must not await in a plain (non-`^:async`) function.**

The examples use these. Each example is `X` in `(defn ^:async f [] (let [val X] ...))`.

```clojure
(defn delayed [v] (js/Promise. (fn [resolve] (resolve v))))
(def port {:port/get (fn [& _] (delayed 5))})
;; get: a local, (let [{:port/keys [get]} port] ...)
```

`Promise` = the promise itself arrives. `value` = it was awaited.

| Form | Example | shadow-cljs | cljs |
|---|---|---|---|
| explicit await | `(await (delayed 5))` | value | value |
| defn call | `(delayed 5)` | Promise | Promise |
| defn call, expression arg | `(delayed (inc 1))` | Promise | Promise |
| `cond` | `(cond :else (delayed 5))` | Promise | Promise |
| `if` | `(if true (delayed 5) nil)` | Promise | Promise |
| `when` | `(when true (delayed 5))` | Promise | Promise |
| `do` | `(do (delayed 5))` | Promise | Promise |
| `case` | `(case 1 1 (delayed 5))` | value | value |
| `try` + `catch` | `(try (delayed 5) (catch :default _ nil))` | value | value |
| `try` + `finally` | `(try (delayed 5) (finally nil))` | value | value |
| `try` without catch/finally | `(try (delayed 5))` | Promise | Promise |
| `let` in value position | `(let [x 5] (delayed x))` | value | value |
| fn from a map | `((:port/get port))` | value | Promise |
| local fn, no args | `(get)` | Promise | Promise |
| local fn, literal or name arg | `(get x)` | Promise | Promise |
| local fn, expression arg | `(get (inc 1))` | value | Promise |
| local fn, vector arg | `(get [1 2])` | value | Promise |

## Why

Statement-shaped forms (`switch`, `try`, `let` with a body) are compiled into an async IIFE. The compiler awaits it.

In ClojureScript's `cljs/compiler.cljc`, `iife-open` / `iife-close` emit `(await (async function(){…})())` for every statement-shaped form in an async context, whether or not it contains an await. The feature's tests (CLJS-3470) cover await inside nested forms, not a nested form that returns a promise.

shadow-cljs picks the arity at run time: `(f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(G) : f.call(null, G))`. The argument appears twice, so an expression argument is first stored in a temp var `G`. That needs a `var` statement, hence a wrapper function, which `^:async` awaits. A literal or a name can be repeated safely, so there is no wrapper. A fn taken from a map (`((:k m))`) is wrapped the same way, for the fn itself.

Plain ClojureScript calls `.call` directly.

## How to check

- Compile the form and read the output: `.shadow-cljs/builds/<build>/dev/out/cljs-runtime/<ns>.js`.
- Keep a behaviour test for anything that races or aborts a promise.

`application.example-backfill/waiting` and `adapters.learner/initialize` exist for this reason.
