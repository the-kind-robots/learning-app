## MODIFIED Requirements

### Requirement: Development builds expose page metrics
A development build SHALL expose in-page measurements — the app-level counters (renders, dispatches with nesting, frames), the standard web metrics, long animation frames, dictionary readiness, and the storage estimate — readable as `window.__metrics()` and resettable as `window.__metricsReset()`.

The measurement library SHALL be fetched at runtime by the development path rather than imported, so that a release bundle carries none of it. Dead-code elimination alone is not sufficient evidence here: a release build was inspected and still contained the instrumentation's own code, so any claim about release contents MUST be made by inspecting the built output.

Map keys SHALL keep the ClojureScript keyword spelling that `clj->js` produces, so a reader accesses `layout-shift`, not `layoutShift`.

#### Scenario: Measurements distinguish known cases
- **WHEN** a state-saving dispatch runs and then a deliberate layout shift is introduced without user input
- **THEN** the render counter increases for the former and the layout-shift score is nonzero for the latter

#### Scenario: Occluded window
- **WHEN** the browser window is occluded or hidden
- **THEN** frame- and paint-dependent metrics stop advancing while dispatch and long-frame counters keep working — a documented property, verified in a painting environment instead

#### Scenario: Release build carries no measurement library
- **WHEN** a release build is produced and its output is inspected
- **THEN** the bundle contains no trace of the web-metrics library — none of its identifiers, such as the interaction grouping it performs

## ADDED Requirements

### Requirement: Standard web metrics are reported under their published definitions
The instrumentation SHALL report LCP, FCP, TTFB, CLS and INP as defined by their specifications, through a maintained implementation rather than a hand-written observer. CLS SHALL be the session-window score, not a running sum. INP SHALL group events by `interactionId` so that one gesture yields one interaction, and SHALL survive back/forward-cache restores without merging two visits into one number.

#### Scenario: One gesture is one interaction
- **WHEN** a single tap fires `pointerdown`, `pointerup` and `click`
- **THEN** the interaction latency is reported once, not three times

#### Scenario: Layout shift is scored, not summed
- **WHEN** shifts occur in separate session windows
- **THEN** the reported CLS is the largest window's score rather than the total of all shifts

### Requirement: Long frames are reported with attribution
The instrumentation SHALL observe `long-animation-frame` entries and record, for each, the frame duration, its `blockingDuration`, and the attributed scripts. It SHALL NOT also report `longtask` entries, which describe the same event with less information.

Where the browser does not support `long-animation-frame`, the list SHALL stay empty rather than raising.

#### Scenario: A blocking frame names its cause
- **WHEN** a script blocks the main thread long enough to produce a long animation frame
- **THEN** the entry reports a non-zero `blockingDuration` and identifies the script responsible

#### Scenario: Unsupported browser
- **WHEN** the browser does not implement `long-animation-frame`
- **THEN** the metrics still read without error and the long-frame list is empty

### Requirement: Dictionary readiness is measurable
The instrumentation SHALL record how long the dictionary takes to become usable, measured on the document's timeline from worker construction to the worker reporting readiness, as a User Timing measure so it is visible in a performance trace. Whatever per-phase durations the worker reports SHALL be carried into the metrics alongside it.

The readiness figure SHALL NOT depend on the worker's own telemetry, which reaches the page only on an uncontrolled load — see the service worker defect that strips the worker's query string.

When the dictionary never becomes ready, no readiness figure SHALL be reported; its absence MUST NOT be represented as zero.

#### Scenario: Cold start against warm start
- **WHEN** the dictionary is loaded with an empty OPFS pool and then again with the file already present
- **THEN** both runs report a readiness duration and the second is markedly shorter

#### Scenario: Phase breakdown on an uncontrolled load
- **WHEN** the dictionary loads on a page the service worker does not yet control
- **THEN** the worker's own phase timings, including the download and import steps, appear in the metrics alongside the readiness figure

#### Scenario: Dictionary never opens
- **WHEN** the worker reports an error instead of readiness
- **THEN** the metrics contain no readiness duration rather than a zero

### Requirement: Storage footprint is measurable
The instrumentation SHALL expose the origin's storage estimate — usage, quota, and the per-kind breakdown where the browser provides one — as `window.__storage()`. Because the underlying API is asynchronous, it SHALL be a separate entry point returning a promise rather than being folded into the synchronous `window.__metrics()`.

#### Scenario: Footprint grows with the dictionary
- **WHEN** the storage estimate is read before and after a cold dictionary load
- **THEN** the reported usage grows by approximately the size of the dictionary file

#### Scenario: Synchronous metrics stay synchronous
- **WHEN** `window.__metrics()` is called
- **THEN** it returns a value directly, not a promise
