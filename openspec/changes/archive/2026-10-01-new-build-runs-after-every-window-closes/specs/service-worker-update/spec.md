## RENAMED Requirements

- FROM: `### Requirement: The service worker activates only on request`
- TO: `### Requirement: A new build activates only when no window of the app is open`

## MODIFIED Requirements

### Requirement: A new build activates only when no window of the app is open
A release build SHALL NOT make a waiting worker skip waiting, neither on install nor on any request: two builds SHALL NOT run at the same time. A newly installed worker SHALL wait while an older one controls any window of the origin, and the browser's own lifecycle SHALL activate it once every such window is closed. A page opened while another window is still open SHALL be served by the build already running. On activation the worker SHALL delete every cache bucket but its own and claim the open pages. The worker SHALL activate on the message `{type: "activate-waiting"}`, which only a development build sends.

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

### Requirement: A tap on the build mark forces a reload in a development build
In a development build the shell's build mark — the line that says which bundle the page loaded — SHALL be the forced reload: a tap SHALL check the registration for an update; if a worker is then waiting or finishes installing, it SHALL post the activation message to it and reload the page once that worker is activated; otherwise it SHALL reload the page. Other open pages SHALL NOT be reloaded. The trace export SHALL be a separate control in the shell's actions row, and a tap on it SHALL NOT reload the page.

#### Scenario: Tap with a new build
- **WHEN** the user taps the build mark and a changed `sw.js` is served
- **THEN** the new worker installs, is activated and the page reloads under it

#### Scenario: Tap with nothing new
- **WHEN** the user taps the build mark and `sw.js` is unchanged
- **THEN** the page reloads

#### Scenario: Tap on the trace export
- **WHEN** the user taps the trace export in the actions row
- **THEN** the trace export runs and the page does not reload

## ADDED Requirements

### Requirement: No page reloads when its controller changes
A page SHALL NOT reload because `navigator.serviceWorker`'s controller changed, and the app SHALL show no control that offers a waiting build. Outside the build mark's tap, a controller change happens only with no page open on the old build, at a first install, or when DevTools forces one in.

#### Scenario: A build waiting, nothing offered
- **WHEN** a new worker is waiting under the running build
- **THEN** no open page shows a control for it and no page reloads

#### Scenario: DevTools' «Update on reload»
- **WHEN** «Update on reload» is on and an open page loads again, so the browser installs and activates a worker and every open page's controller changes
- **THEN** no page reloads for it
- **AND** the page that loaded again loaded once

## REMOVED Requirements

### Requirement: A waiting worker is offered to the user
**Reason**: Taking a waiting build while windows are open makes two builds run at once, which can write the local databases under two data models. The build now waits for every window to close.
**Migration**: None. A new build reaches the user on the next open after every window of the app is closed.

### Requirement: Every page reloads once when its controller changes
**Reason**: In a release build no page is open when a new build activates, so there is nothing to move. A reload on every controller change also looped for as long as DevTools' «Update on reload» was on (#517, under #515).
**Migration**: None.
