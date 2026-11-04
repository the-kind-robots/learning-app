# UI Implementation Principles

This document captures the UI implementation principles for this project.

## Goals

- Keep UI minimal, fast, and easy to understand.
- Prefer clarity over cleverness.
- Avoid JS unless required by htmx limitations.

## Markup (Hiccup)

- Use explicit attrs maps and semantic elements.
- Avoid raw HTML strings.
- Keep structure shallow and readable.

## Interactions (htmx)

- Prefer htmx attributes over custom JS.
- Keep targets local and explicit.
- Use `hx-indicator` for loading state.

## Styling (plain CSS, no build step)

- One block per component, its rules inside `@scope (.block) { … }`; the root is `:scope`.
- Elements take short domain names (`.value`, not `.word-item__value`): the scope keeps them
  from matching outside the block. Never reuse an unscoped class name (a component root,
  a utility) as an element name.
- A block that renders another block inside stops at it: `to (.child)`, or `to (.child > *)`
  when the parent still places the child's root.
- When one element is both a block root and another block's element (`.page-footer.footer`),
  the nearer root wins ties — scope proximity comes before source order. A block that other
  blocks place writes its root rules as `:where(:scope)`, so the placing block wins.
- Variants and states are attributes, not classes: `data-accent="owl"`, `aria-current`,
  `:disabled`.
- Cascade order is the `@layer` list in `resources/public/css/styles.css`.
- Keep block names short and domain-specific.

## Motion

- Use CSS transitions for state changes.
- Keep durations short and subtle.
- Respect `prefers-reduced-motion`.

## Accessibility

- Preserve semantic hierarchy and focus order.
- Ensure text remains readable at small sizes.
- Provide visible focus states when interactive.
