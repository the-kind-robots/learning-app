# 0014. The service worker activates on request; every page reloads on controllerchange

- Status: accepted
- Date: 2026-09-17

## Context

`sw.js` names its cache bucket after the build and, on activate, deletes every
other bucket. A worker that activated under a live page would delete the
bucket that page still loads its assets from, which is why `install` never
calls `skipWaiting` (#278). The price was that a new build waited until every
page of the origin was gone, and on Android a swiped-away PWA is not gone
(#408).

## Decision

The worker keeps waiting by default. It activates only when a page posts it
`{type: "activate-waiting"}`, and only the user makes a page post it: the tap
on «Обновить», in every build, or a tap on the red D in a development
build. No build activates a waiting worker by itself — a development watch
writes a new worker on every recompile, and taking each one would reload every
open page and throw away the hot reload.

What makes the activation safe is on the page side: every page that started
under a controlling worker reloads itself once on `controllerchange`. The
bucket is deleted, but no page keeps running on it. A page that started
without a controller does not reload when the first worker claims it.

Any future change to the worker's lifecycle keeps both halves: no
`skipWaiting` outside the message, and no page that survives a controller
change without reloading.

## Consequences

- A new build is offered, never forced; the user decides when the reload
  happens. A development build recompiles into a waiting worker on every save
  and «Обновить» stays on screen until it is taken.
- Two open tabs both reload when one of them activates the worker; a lesson
  in the other tab is lost, which is the same outcome as the update the user
  asked for.
- The message name is part of the contract between `sw.js` and the client.
