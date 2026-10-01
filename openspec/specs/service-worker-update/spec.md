# service-worker-update Specification

## Purpose
Define when a new service worker takes over: in a release build it waits until every window of the app is closed, no page reloads when its controller changes, a development build's build mark takes it at once, and the registration checks for a new worker when the app comes back into view.

## Requirements

### Requirement: A new build activates only when no window of the app is open
A release build SHALL NOT make a waiting worker skip waiting, neither on install nor on any request: two builds SHALL NOT run at the same time. A newly installed worker SHALL wait while an older one controls any window of the origin, and the browser's own lifecycle SHALL activate it once every such window is closed. A page opened while another window is still open SHALL be served by the build already running. On activation the worker SHALL delete every cache bucket but its own and claim the open pages. The worker SHALL activate on the message `{type: "activate-waiting"}`, which only a development build sends and which a packaged build SHALL ignore.

#### Scenario: A new worker waits
- **WHEN** a page with a controlling worker fetches a changed `sw.js`
- **THEN** the new worker installs and stays in the waiting state
- **AND** the controlling worker keeps serving every open page, including a page opened after the new worker installed

#### Scenario: Every window closed
- **WHEN** a worker is waiting and every window of the app is closed
- **THEN** the next page opened runs the new build

#### Scenario: The activation message
- **WHEN** a development build's page posts `{type: "activate-waiting"}` to the waiting worker
- **THEN** the worker activates, deletes the other cache buckets and claims the pages

#### Scenario: The activation message in a packaged build
- **WHEN** a page posts `{type: "activate-waiting"}` to the waiting worker of a packaged build
- **THEN** the worker stays waiting

### Requirement: The registration checks for an update when the document becomes visible
Every time the document becomes visible the app SHALL ask the registration to update, so a phone returning to the app fetches the current `sw.js`.

#### Scenario: Coming back to the app
- **WHEN** the document's visibility changes to visible
- **THEN** the service worker registration fetches `sw.js` again

### Requirement: A tap on the build mark forces a reload in a development build
In a development build the shell's build mark — the line that says which bundle the page loaded — SHALL be the forced reload: a tap SHALL check the registration for an update; if a worker is then waiting or finishes installing, it SHALL post the activation message to it and reload the page once that worker is activated or has gone redundant, or after a short wait if neither happens; otherwise it SHALL reload the page. Other open pages SHALL NOT be reloaded. The trace export SHALL be a separate control in the shell's actions row, and a tap on it SHALL NOT reload the page.

#### Scenario: Tap with a new build
- **WHEN** the user taps the build mark and a changed `sw.js` is served
- **THEN** the new worker installs, is activated and the page reloads under it

#### Scenario: Tap with nothing new
- **WHEN** the user taps the build mark and `sw.js` is unchanged
- **THEN** the page reloads

#### Scenario: Tap on the trace export
- **WHEN** the user taps the trace export in the actions row
- **THEN** the trace export runs and the page does not reload

### Requirement: No page reloads when its controller changes
A page SHALL NOT reload because `navigator.serviceWorker`'s controller changed, and the app SHALL show no control that offers a waiting build. Outside the build mark's tap, a controller change happens only with no page open on the old build, at a first install, or when DevTools forces one in.

#### Scenario: A build waiting, nothing offered
- **WHEN** a new worker is waiting under the running build
- **THEN** no open page shows a control for it and no page reloads

#### Scenario: DevTools' «Update on reload»
- **WHEN** «Update on reload» is on and an open page loads again, so the browser installs and activates a worker and every open page's controller changes
- **THEN** no page reloads for it
- **AND** the page that loaded again loaded once
