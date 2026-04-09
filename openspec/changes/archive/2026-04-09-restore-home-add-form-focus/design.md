## Context

The home add-word flow submits with `hx-swap="none"` and returns an out-of-band replacement of the form shell. After the OOB swap, the fresh form instance has no explicit focus restoration, so desktop users lose caret position even though the UI is otherwise ready for the next entry.

## Goals / Non-Goals

**Goals:**
- Return focus to `#new-word-value` after a successful submit on desktop-style devices.
- Keep the behavior local to the home add flow without changing routing or broader HTMX update strategy.
- Avoid reopening the soft keyboard automatically on touch-first/mobile devices.

**Non-Goals:**
- Redesigning the add-word form.
- Adding a general-purpose client-side focus manager.
- Changing validation or autocomplete behavior.

## Decisions

### 1) Restore focus from the OOB replacement itself
- Attach the post-submit focus behavior to the OOB-rendered home add form.
- Run the focus logic after HTMX has settled the replacement so the new input element exists.

### 2) Gate focus restoration to desktop-style pointers
- Use a pointer capability heuristic (`matchMedia('(pointer:fine)')`) to focus only where persistent keyboard entry is expected.
- Do not force focus on coarse-pointer/mobile devices.

### 3) Keep the implementation local and low-risk
- Implement the behavior as a tiny view-level HTMX hook in `views.home`.
- Verify the result with a real browser add-word flow instead of introducing extra runtime infrastructure.

## Risks / Trade-offs

- **Risk:** Some hybrid devices may report an unexpected pointer type.  
  **Mitigation:** Use a conservative desktop-oriented heuristic and verify the common desktop path first.

- **Risk:** Focus restoration could run on error responses if attached too broadly.  
  **Mitigation:** Keep it on the success-path OOB form replacement only.

## Migration Plan

1. Add desktop-only focus restoration to the OOB home add form.
2. Run a browser flow that adds multiple words in sequence and confirm the German field keeps focus after each successful submit.
3. Sanity-check that mobile behavior is not explicitly forced by the new hook.

Rollback: remove the new HTMX focus hook from the OOB home add form.

## Open Questions

- Do we want the same desktop focus-restoration behavior for inline vocabulary editing later, or should it remain scoped to home add flow?
