## 1. Baseline Interaction Stability

- [ ] 1.1 Capture current mobile interaction behavior for home composer (keyboard open/close, focus switch, submit) and document reproducible steps.
- [ ] 1.2 Identify and remove CSS rules that toggle major layout visibility during routine interaction states.

## 2. Home Composer Geometry Stabilization

- [ ] 2.1 Replace keyboard-sensitive fixed/vh composer positioning with geometry-stable mobile layout primitives.
- [ ] 2.2 Ensure form and sibling actions remain structurally mounted during focus transitions.
- [ ] 2.3 Keep suggestion-container behavior stable without overflow mode thrash.
- [x] 2.4 Keep mobile word edit on the vocabulary list from fighting the keyboard, scroll, and footer while opening.

## 3. Submit and HTMX Churn Reduction

- [ ] 3.1 Keep add-word submit updates local (no page-shell replacement for successful submit).
- [ ] 3.2 Stabilize submit button request-state visuals while preserving double-submit protection.
- [ ] 3.3 Tune autocomplete trigger cadence and synchronization to avoid late-response repaint after submit/reset.

## 4. Overflow and Sizing Safety

- [ ] 4.1 Remove viewport-width-based control sizing that can induce horizontal overflow.
- [ ] 4.2 Add root/container overflow safeguards and verify they do not hide intended content.
- [ ] 4.3 Validate no horizontal scroll on home, lesson, and vocabulary at mobile widths.

## 5. Verification

- [ ] 5.1 Manual QA on mobile viewports: keyboard open, focus switch between two inputs, repeated submit, and route transitions.
- [ ] 5.2 Confirm that `НАЧАТЬ УРОК` does not flash during input focus changes.
- [ ] 5.3 Run `npx shadow-cljs compile app` and address any regressions.
- [x] 5.4 Verify mobile word-list edit opens without a visible layout thrash or squeezed form.
