# 0024. Screen changes run as view transitions

- Status: accepted
- Date: 2026-10-25

## Context

Replicant renders the whole shell from state into `body`. A screen change replaces the screen's DOM, so the screen being left no longer exists to be animated. The owner chose motion for screen changes on a phone (#634): slides for depth, a fade-through for the lesson, a slide between lesson tasks, a gap closing after a deletion.

## Decision

A screen change runs as a view transition (`document.startViewTransition`) around the render. The kind of change is derived from the state before and after the render, and is the transition's type (`startViewTransition({types})`); CSS keyframes under `:active-view-transition-type` on the `::view-transition-*` pseudo-elements draw it. A change whose DOM update is not a render of new interface state, such as a deletion written to memory, runs its update inside the transition itself.

Motion inside a screen — something appearing, shaking, highlighting — is plain CSS on the mounted element, not a view transition.

## Consequences

- One wrapper decides whether to animate: phone width, reduced motion, API support and the browser's own back animation are checked in one place.
- While a transition runs the page takes no pointer input; transitions stay short (180–240 ms).
- A render asked for before the transition has captured the old screen waits for the capture, a frame at most.
- Elements carry a `view-transition-name` only under the transition type that needs them, so other transitions keep them in the screen's snapshot.
