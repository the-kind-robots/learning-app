# repo-browser-debugging Delta

## MODIFIED Requirements

### Requirement: Development builds expose page metrics
A development build SHALL expose in-page measurements — the app-level counters (renders, dispatches with nesting, frames), the standard web metrics, the unfiltered layout shift, long animation frames, dictionary readiness, and the storage estimate — readable as `window.__metrics()` and resettable as `window.__metricsReset()`.

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

### Requirement: Shifts the user's own input caused are measured separately
CLS is defined to exclude every `layout-shift` entry carrying `hadRecentInput`, so it cannot see a layout that moves while the user types. The instrumentation SHALL therefore report, beside the standard CLS and off an observer of its own, the score over every entry regardless of that flag, the portion of it CLS excluded, and the entries themselves. The standard CLS SHALL keep its published definition rather than being widened to compensate.

#### Scenario: A shift the user's own typing caused
- **WHEN** the layout moves while the user types, inside the window `hadRecentInput` covers
- **THEN** the standard CLS stays at zero while the unfiltered score carries the shift and the excluded portion equals it, so a test can assert on the difference

#### Scenario: A shift no interaction preceded
- **WHEN** the layout moves with no user input near it
- **THEN** the standard CLS and the unfiltered score both carry it and the excluded portion stays at zero, which is what shows the two counters are reading the same stream

#### Scenario: The viewport changes size
- **WHEN** the viewport shrinks, as it does when a software keyboard opens
- **THEN** no `layout-shift` entry is emitted at all, so neither counter reports the movement and a spec about keyboard-driven reflow SHALL assert on element geometry instead
