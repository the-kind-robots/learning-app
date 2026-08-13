# repo-browser-debugging Delta

## MODIFIED Requirements

### Requirement: Development builds expose page metrics
A development build SHALL expose in-page counters — renders, dispatches with nesting, frames, layout shift both excluding and including input-caused entries, long tasks, slow interactions — readable as `window.__metrics()` and resettable as `window.__metricsReset()`. Release builds SHALL NOT contain the instrumentation.

#### Scenario: Measurements distinguish known cases
- **WHEN** a state-saving dispatch runs and then a deliberate layout shift is introduced without user input
- **THEN** the render counter increases for the former and the layout-shift score is nonzero for the latter

#### Scenario: A shift the user's own typing caused
- **WHEN** the layout moves while the user types, inside the window `hadRecentInput` covers
- **THEN** the CLS-filtered score stays at zero while the unfiltered score carries the shift, so a test can assert on it

#### Scenario: Occluded window
- **WHEN** the browser window is occluded or hidden
- **THEN** frame- and paint-dependent metrics stop advancing while dispatch and long-task counters keep working — a documented property, verified in a painting environment instead
