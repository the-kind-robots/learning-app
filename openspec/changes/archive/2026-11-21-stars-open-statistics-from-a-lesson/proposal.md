## Why

Today's stars open the statistics on home, the word list and the themes (#786), but not in a lesson (#830). Screens
stack (ADR-0026), so the statistics can open over a lesson; closing them must give the lesson back as the learner
left it, its typed answer and placed tiles included. The shell drew only the top screen and made the one beneath
anew, so that needs the stack drawn whole.

## Changes

- The stars are a button in a lesson too, and `Alt`+`S` there opens the statistics.
- Every open window stays on the page, the top one drawn: a window shown by closing the ones above it is as it was
  left, and a page opened is computed anew. This holds for every screen, not the lesson alone.
- The screen on display is the top of the stack; `:page/current` is removed.
- On a phone a screen opened over the lesson slides deeper and back; the lesson's fade is for opening and closing it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `statistics`: today's stars open the statistics from a lesson.
- `app-navigation`: a window shown again is as it was left; `Alt`+`S` opens the statistics in a lesson.
- `screen-transitions`: a screen over the lesson slides; the fade is the lesson's opening and closing.

## Decisions

- The open windows stay on the page (ADR-0029), over a rule for the lesson alone (keep it while covered, skip its
  computing on return) and over keeping the typed answer in state: the lesson is then not special, and the answer
  field keeps its tiles, which only its element holds. Chosen by the owner.
- Only the top window is drawn, each window into an element of its own, a Replicant root, over drawing the whole
  stack every render, which made the lesson's render grow with the windows beneath it (ADR-0029). Chosen by the
  owner, with the shell drawing those elements, over making them by hand outside Replicant.
- Opening a page computes it anew, one open beneath included; closing the ones above shows a window as it was left,
  computing nothing, over refreshing it from memory. A window with no drawing opens as it comes on top, as its element
  tells, over opening every window of a stack as it arrives. Chosen by the owner: choosing a collection opens home.
- The stack alone names the screen on display, over `:page/current` written a step after it.
- A trial left for the statistics keeps counting its time to answer, over a rule that voids it: retention caps it.
- `:in` and `:out` of a page's transitions are its opening and closing. Chosen by the owner, over the lesson's fade.
