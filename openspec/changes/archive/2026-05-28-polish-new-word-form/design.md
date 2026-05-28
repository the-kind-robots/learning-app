## Constraints

- Cannot break the existing arrow/Tab/Escape behavior on the autocomplete.
- Cannot regress the click-to-pick path on suggestion items (the blur fix must not race the click handler).
- The dictionary build is already covered by tests; new filtering must not break them and must keep build time stable.
- View Transitions on the home screen are unrelated and must not be affected.

## Decisions

### Enter follows the existing Tab pattern

Both keys "confirm the highlighted suggestion". Reuse `select-item` and the same `:effect/prevent-default → save → focus` chain. No second selection model.

### Blur dismissal uses pointerdown-on-list before blur fires

If we naively dispatch suggestion-clear on `blur`, the blur fires before the click on the suggestion li and the click never resolves. Two options were considered:

- **A.** Defer the clear via `setTimeout` so the click lands first. Reliable but introduces a flash and a tiny pseudo-async hack in the action layer.
- **B.** Use `pointerdown` on the suggestion item to prevent the blur (or do the selection during pointerdown). Sidesteps the race entirely; the input loses focus only after the selection has been committed.

We pick **B**: add `:on {:pointerdown ...}` to the suggestion `<li>` that calls `:effect/prevent-default` so the input does not blur during the click; the existing click handler still fires and commits selection. Outside clicks blur normally and the suggestions clear without delay.

### Surface-form filter: blacklist tags rather than whitelist

A whitelist of "real" inflectional tags (nominative/singular/etc.) would be more robust but requires per-POS knowledge of every tag the Kaikki dump produces. A blacklist of `feminine`, `masculine`, `abbreviation` catches the known leaks ("Mutter" cross-ref under "Vater", "Angest." under "Angestellter") without committing the materializer to a closed inflection vocabulary. Add more tags to the blacklist if other leaks surface.

### Focus ring style is a global utility

Buttons across the home flow use a mix of `.big-button` and component-local classes. Add the `:focus-visible` ring to:

- `.big-button` (shared, covers the form's submit and the lesson CTA)
- `.home__words-button` (component-local)
- `.app-shell__corner-icon` (already a focusable anchor)

The ring is a 2px solid outline in `--color-macaw` (the brand accent already used for input focus), with a 2px offset so it sits outside the element.
