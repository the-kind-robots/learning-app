## Context

This change is a UI cleanup pass over the home and lesson flows. The reported problems are not isolated visual nits: they cluster around state transitions and re-renders, especially when the screen changes between empty/non-empty home states, when inline editing is active, and when lesson results are shown.

The plan should keep the scope practical:
- fix the known visible glitches
- verify on both desktop and mobile layouts
- opportunistically fix adjacent issues in the same flow when they share the same cause

## Goals / Non-Goals

**Goals**
- Make home-screen animation and layout changes feel intentional rather than jittery.
- Prevent mobile help/overlay UI from covering the word-entry input.
- Make word-list editing and last-word deletion transitions visually stable.
- Make lesson prompt/result layout stable during answer checking.
- Use this change to catch and fix nearby UI regressions discovered while verifying those flows.

**Non-Goals**
- Full visual redesign of the home or lesson screens.
- Reworking lesson mechanics or review logic unrelated to UI stability.
- Large interaction redesigns that are not needed to remove the glitches.

## Decisions

### 1) Treat home UI problems as one motion/layout stability bucket
- Fix footer animation timing, add-word mobile overlap, edit-row jitter, and last-word empty-state jerk as one home UI cleanup area.
- Prefer root-cause fixes in state/render transitions over isolated CSS patches when possible.

### 2) Treat lesson answer-check jumping as layout-stability work
- Keep the lesson prompt and surrounding result UI visually stable when answers are checked.
- Avoid layout shifts caused by conditional rendering, mismatched spacing, or result-panel insertion.

### 3) Allow nearby glitch fixes discovered during verification
- If a closely related UI glitch appears in the same home/lesson flow while verifying this work, include it in the same change instead of opening a separate tiny task.
- Keep that extension narrow: same screens, same interaction cycle, same stability goal.

## Risks / Trade-offs

- **Risk:** Tightening animations/transitions could accidentally remove feedback the user still needs.  
  **Mitigation:** Keep meaningful state feedback, but limit motion to the transitions that should actually draw attention.

- **Risk:** Mobile-specific fixes can regress desktop layout if handled only with broad CSS changes.  
  **Mitigation:** Verify both coarse-pointer/mobile and desktop behavior after each change.

- **Risk:** “Fix nearby glitches too” can sprawl.  
  **Mitigation:** Keep the scope constrained to home/lesson UI stability issues discovered during direct verification of this task.

## Verification Plan

1. Verify home screen on desktop:
   - returning to home does not replay the footer animation unless the first-word-add transition actually happened
   - edit row does not jitter
   - deleting the last word transitions cleanly to the empty-state screen
2. Verify home screen on mobile:
   - add-word helper/overlay does not cover the active input
   - edit and delete interactions remain stable
3. Verify lesson screen:
   - prompt panel does not jump during answer checking
   - result rendering does not shift the main task card unexpectedly
4. Fix nearby UI glitches discovered in the same verification path if they share the same cause or screen flow.
