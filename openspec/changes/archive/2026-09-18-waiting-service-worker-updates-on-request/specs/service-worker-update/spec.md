## ADDED Requirements

### Requirement: The service worker activates only on request
The service worker SHALL NOT skip waiting on its own: a newly installed worker stays waiting while an older one controls the origin. It SHALL activate when a page posts it the message `{type: "activate-waiting"}`, and on activation it SHALL delete every cache bucket but its own and claim the open pages, as before.

#### Scenario: A new worker waits
- **WHEN** a page with a controlling worker fetches a changed `sw.js`
- **THEN** the new worker installs and stays in the waiting state
- **AND** the controlling worker keeps serving the page

#### Scenario: The activation message
- **WHEN** a page posts `{type: "activate-waiting"}` to the waiting worker
- **THEN** the worker activates, deletes the other cache buckets and claims the pages

### Requirement: A waiting worker is offered to the user
The app SHALL detect a waiting worker — one already waiting when the registration resolves, or one that reaches the installed state while a controller exists — and SHALL show an «Обновить» control in the app shell's actions row; tapping it SHALL post the activation message to the waiting worker. No build SHALL post the message unasked: a development build offers the worker the same way, so a recompile does not reload the open pages.

#### Scenario: A waiting worker shows the control
- **WHEN** the app detects a waiting worker
- **THEN** the shell shows «Обновить»
- **AND** tapping it activates the waiting worker

#### Scenario: A development build also waits to be asked
- **WHEN** a development build detects a waiting worker
- **THEN** the shell shows «Обновить» and the worker stays waiting
- **AND** no page reloads until the control is tapped

#### Scenario: No waiting worker
- **WHEN** no worker is waiting
- **THEN** the shell shows no «Обновить» control

### Requirement: Every page reloads once when its controller changes
A page that started under a controlling worker SHALL reload itself once when `navigator.serviceWorker`'s controller changes, so no page keeps running on a deleted cache bucket. A page that started without a controller SHALL NOT reload when the first worker claims it. A second controller change before the reload completes SHALL NOT reload again.

#### Scenario: Two tabs after an activation
- **WHEN** two tabs of the origin are open under the old worker and one of them activates the waiting worker
- **THEN** both tabs reload and are controlled by the new worker

#### Scenario: First visit
- **WHEN** a page loads with no controlling worker and the first worker installs, activates and claims it
- **THEN** the page does not reload

### Requirement: The registration checks for an update when the document becomes visible
Every time the document becomes visible the app SHALL ask the registration to update, so a phone returning to the app fetches the current `sw.js`.

#### Scenario: Coming back to the app
- **WHEN** the document's visibility changes to visible
- **THEN** the service worker registration fetches `sw.js` again

### Requirement: A tap on the build mark forces a reload in a development build
In a development build the shell's build mark — the line that says which bundle the page loaded — SHALL be the forced reload: a tap SHALL check the registration for an update; if a worker is then waiting or finishes installing, it SHALL post the activation message and let the controller change reload the page; otherwise it SHALL reload the page. The trace export SHALL be a separate control in the shell's actions row, and a tap on it SHALL NOT reload the page.

#### Scenario: Tap with a new build
- **WHEN** the user taps the build mark and a changed `sw.js` is served
- **THEN** the new worker installs, is activated and the page reloads under it

#### Scenario: Tap with nothing new
- **WHEN** the user taps the build mark and `sw.js` is unchanged
- **THEN** the page reloads

#### Scenario: Tap on the trace export
- **WHEN** the user taps the trace export in the actions row
- **THEN** the trace export runs and the page does not reload
