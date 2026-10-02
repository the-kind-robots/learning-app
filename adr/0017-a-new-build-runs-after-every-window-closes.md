# 0017. A new build runs after every window of the app closes

- Status: accepted
- Date: 2026-10-01
- Supersedes: ADR-0014 (in part)

## Context

ADR-0014 let the user take a waiting build from inside the app. «Обновить» asked the worker to skip waiting, and every page reloaded on `controllerchange` to move onto it. Two problems followed. The reload on every controller change looped for as long as DevTools' «Update on reload» was on (#517, under #515). And a takeover with windows open runs two builds at once. Both builds write the same local databases, and their data models can differ.

## Decision

Two builds never run at the same time. A release build never makes a worker skip waiting. A new build waits until every window of the app is closed, and the browser activates it then. Nothing moves at activation, so no page reloads on a controller change, and the app offers no update control.

A development build keeps one way in: the build mark. A recompile has to reach a tab that stays open. Only development bundles send the activation message.

The requirements are in [service-worker-update](../openspec/specs/service-worker-update/spec.md). The rest of ADR-0014 — the worker's bucket per build and the check on return to the foreground — stands.

## Consequences

- An app kept open all the time updates only when all its windows close.
- A development tab that takes a build reloads itself. Other development tabs keep their code until they reload.
- Any future change that needs a running page to switch builds must reckon with the two data models, not reach for `skipWaiting`.
