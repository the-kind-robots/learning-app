## Context

The behaviour this change must produce is in `specs/main-thread-runtime/spec.md`. The mechanism and the bisect are on #515.

## Goals / Non-Goals

**Goals:** no life-cycle hook writes the store during a render; the browser specs catch a hook that does.

**Non-Goals:** changing `install-render!` (#213 stays as it is); coalescing renders.

## Decisions

**The announcer finds the region by id instead of keeping its node in the store.** The region is always rendered and never replaced, so looking it up by its id at announce time finds the same node that the store used to hold. The region's markup and its id live in a namespace of their own, `application.shell`, which `application` and the page views both require; `application` requires every page view, so the id cannot live there. It enters the data where the learner starts the action: the collection delete control puts `application.shell/status-region-id` into its payload (`{:id :name :status-region}`), and `:effect/delete-collection` and `:action/show-deleted` pass it on to `[:effect/announce status-region message]`. No action or effect holds a layout id of its own. *Alternative:* have the watch catch writes made during a render and render again after it returns. That would work around the hook instead of removing the write, and it would hide the next hook that does the same.

**The suite checks it through Replicant's own report.** A development build reports a render requested during a render as a console group titled "Replicant warning: Triggered a render while rendering". The report is sent from an animation frame. `test/browser/fixtures.js` extends Playwright's `context` fixture: it listens to the console of every page of the context, runs frames on every open page after the test, waits a short quiet spell, and fails the test if the report appeared. Each spec imports `test` and `expect` from there. `render-guard.spec.js` renders during a render on purpose and is expected to fail through the guard. The specs run against a development build locally and on CI, so the report is always on.

**The guard is broader than the rule, on purpose.** Replicant also reports a store write from a DOM event that its own DOM change fires synchronously: Chrome fires `blur` on a focused field while Replicant removes it, and the field is still connected at that moment. The word field's blur then dismisses its suggestions mid-render. That case is harmless, because the deferred frame draws the same screen that is on display, and it is not fixed. No spec takes that path today; one that does will meet the report, and `test/browser/README.md` says so.

## Risks / Trade-offs

- [A context the spec launches itself] → `collections-gestures.mobile.spec.js` launches a persistent context for its hyphenation check; that context is not watched. The check is skipped on CI.
- [Release builds] → they carry no assertions; the check exists only in the suite.
