# Design: mobile-add-form-stability

## Why the dictionary is a fixture served over an environment variable

Suggestions need a dictionary, so a spec about the suggestion list needs one
too. Three ways were available.

**Serve the shipped dictionary.** It is 37 MB and committed, so nothing to
build — but every Playwright test gets a fresh context, OPFS starts empty, and
`sqlite3-worker.js` fetches and imports the whole file before posting `ready`.
That cost lands on every test, and no spec had ever waited for it, so the price
would have been paid silently.

**Intercept `/dictionary/*` with `page.route`.** No backend change, but the
requests come from a Web Worker rather than the page, and worker interception
is not something to build a suite on.

**A small dictionary from a directory the backend is told about.** Chosen. The
backend reads `LEARNING_APP__DICTIONARY_DIR` once at load; unset, every byte
and header on the classpath path is what it was before. Production leaves it
unset — a deployment can be held to the artifact inside its own jar, never to a
path on the host.

Two details worth keeping. The manifest read became a `delay`: the environment
variable is a deployment fact and stays a load-time read, but a directory
without a `manifest.edn` must answer 404 the way a missing resource already
does, not break the namespace at load. And the fixture is **generated**, not
committed: `write-manifest!` computes the sha256 from the file it just wrote,
so a committed pair would rot the first time someone edited the source EDN
without rebuilding. `target/` is already ignored.

`dictionary.fixture/build` reuses the production emitters rather than writing
its own schema. A fixture is the shipped artifact in miniature; a hand-rolled
schema would drift from the one `adapters.dictionary/completions` queries the
moment either moved. It refuses to write into `resources/dictionary`, because
`write-dictionary!` deletes and renames `dictionary.sqlite` inside its output
directory.

## Why two counters instead of changing the one

`hadRecentInput` is the CLS rule and the existing counter should keep it: it
answers "would this page score badly", and that question still matters. The
counter this spec needs answers a different one — "did the layout move while
the user was typing" — and by construction the CLS rule hides exactly that.
Both are computed in a single observer callback so they cannot disagree about
which entries they saw.

Measured here, the difference is the whole finding: 0.053 unfiltered against
0.000 filtered for the same seven keystrokes.

## Why the mobile project waits for the desktop one

A long task is main-thread time, so a browser sharing the machine with several
others manufactures them. Measured five runs each way on the same code: alone,
zero long tasks and no slow interactions every time; five in parallel, up to
nine long tasks per run and keystrokes taking 128-336 ms. `dependencies` costs
nothing — the mobile project holds one spec — and buys a quiet machine.

The spec asserts on `slow-interactions` rather than `long-tasks`. The long-task
threshold is 50 ms by definition, and an unminified development build under WSL
crosses it on background work alone, with nothing typed into the page. Event
entries are anchored to the user's own input, so they measure this form instead
of the machine's mood. Long tasks are still logged: a number worth watching is
not the same as a number worth failing on.

## Why the list overlays instead of staying in flow

The issue left this open, to be decided from measurements. The measurements
decided it: 0.053-0.058 unfiltered shift is past the 0.05 budget on every run,
and the shift is not confined to what sits below the list — `.home__content`
centres the add panel, so half the added height travels upward and takes the
value field with it, 92 px out from under the finger that is typing into it.

The in-flow layout was not careless: its 180 ms height animation is what kept
the score as low as 0.053, by spreading the movement over a dozen frames rather
than one. But an animation can only make a layout shift cheaper. An overlay
does not shift at all, it is what the desktop already does, and `.home__add`
already sets `overflow: visible` for it. The phone keeps one thing of its own —
`max-height: min(240px, 34svh)`, because the software keyboard takes roughly a
third of the screen.
