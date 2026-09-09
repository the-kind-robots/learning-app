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

## Why an unfiltered shift counter beside the standard CLS

`standard-page-metrics` replaced the hand-rolled shift counter with the
web-vitals library, so CLS is now the real session-window score. It still
cannot see this defect, and that is not a bug in the library — CLS is defined
to drop every `layout-shift` entry carrying `hadRecentInput`, which the browser
sets on everything within 500 ms of a keystroke.

Measured on this form, which is the whole finding:

| | entries | sum | of which `hadRecentInput` | web-vitals CLS |
|---|---|---|---|---|
| list in flow | 11 | 0.028 | 0.028 (all of it) | 0.000 |
| list overlaid | 0 | 0.000 | 0.000 | 0.000 |
| control: a shift with no input near it | 2 | 0.224 | 0.000 | 0.224 |

The control is what makes the first row a statement about the metric rather
than about the instrument: the same page, the same observer, reports 0.224 the
moment the shift is not attributable to typing. CLS is looking away on purpose.

A software keyboard is invisible to it twice over. Shrinking the viewport from
844 to 520 — what the keyboard does to the page — emitted no `layout-shift`
entries at all, so no counter built on that stream can see it either.

Hence `:layout-shift`, off the same observer, keeping `:score` (every entry),
`:input-excluded` (the part CLS discarded) and the entries themselves. And
hence the spec's real assertions being `boundingBox()` comparisons: geometry is
the only instrument that measures what is actually being asked about.

## Why the mobile project waits for the desktop one

A long task is main-thread time, so a browser sharing the machine with several
others manufactures them. Measured five runs each way on the same code: alone,
zero long tasks and no slow interactions every time; five in parallel, up to
nine long tasks per run and keystrokes taking 128-336 ms. `dependencies` costs
nothing — the mobile project holds one spec — and buys a quiet machine.

The spec asserts on INP rather than on long frames. A long frame is main-thread
time, and an unminified development build under WSL produces them on background
work alone, with nothing typed into the page. INP is anchored to the user's own
input, so it measures this form instead of the machine's mood. Long frames are
still logged: a number worth watching is not the same as a number worth failing
on. Measured over five sequential runs, INP came out 40-88 ms against its own
200 ms "good" boundary; the same spec run against the in-flow layout produced
272 ms, so the headroom belongs to the fix, not to the threshold.

One trap, since `__metricsReset()` runs mid-spec: web-vitals keeps its own
state, so clearing our copy of INP does not rewind the library, and after the
reset it re-reports only if a new worst interaction appears. The spec therefore
reads INP before the reset as well and takes the larger of the two.

## Why the list overlays instead of staying in flow

> **Superseded by #373** (`openspec/changes/phone-suggestions-in-flow/`). The
> overlay is gone on phones: the list opens in the flow again, bounded by
> `max-height: min(176px, 34svh)` instead of a fixed height. The reasoning
> below is left as it was — it was right about the measurements it had. What
> changed is the input, not the logic: the device build turned out to carry a
> *fixed* height, so the phone was paying the full 180 px push at one row as at
> ten, and a ceiling gives the shrinking this section wanted without the
> occlusion the overlay brought (#349). The 0.05 shift budget below moved with
> the layout and is no longer the standing one: it was measured on a list whose
> open height was fixed and which therefore never resized, while a content-sized
> list resizes on every change of match count. #373 raised it to 0.06 and wrote
> out why beside `SHIFT_BUDGET` in `add-form-stability.mobile.spec.js`.

The issue left this open, to be decided from measurements.

Re-measured after merging master, because master moved the ground. When this
was first measured, `.home__content` still had `justify-content: center`, so
half the list's added height travelled upward and took the value field 92 px
out from under the finger. Master has since anchored that container to the top
(`justify-content: flex-start`) for exactly this reason. On the merged tree the
value field and the panel title no longer move at all with the list in flow —
that half of the original argument is now master's fix, not this one's.

What remains, measured on the merged tree: the list in flow adds 184 px between
the value field and everything under it, so the translation field and the
submit button both drop 184 px while the user is still typing the first field.
The unfiltered shift score is 0.028, which is *inside* the 0.05 budget — so the
budget is no longer what decides this. The 184 px displacement is.

The in-flow layout was not careless: its 180 ms height animation is what keeps
the score that low, by spreading the movement over a dozen frames rather than
one. But an animation can only make a layout shift cheaper. An overlay does not
shift at all, it is what the desktop already does, and `.home__add` already
sets `overflow: visible` for it. The phone keeps one thing of its own —
`max-height: min(240px, 34svh)`, because the software keyboard takes roughly a
third of the screen.

The overlay has a cost of its own and it is not measured away: while the list
is open it covers the translation field completely (56 px of 56) and 3 px of
`ДОБАВИТЬ`. Trading a 184 px jump for occlusion is a judgement, not a
measurement, and it is tracked separately as #349 rather than settled here.
