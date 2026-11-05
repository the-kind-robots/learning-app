## Why

A star for a lesson just passed lights up in place among the others; the owner wants it to draw the eye: grow in, the row making room for it, and five gold stars visibly become a purple one (#778).

## Changes

- A star that joins the bar grows in at the right past its size and settles; the row, centred in the bar, widens with it, so the stars before it slide left. It finishes on any screen, and a lesson from another device grows in too.
- The fifth gold star grows in like the others and turns purple while the four before it gather into it.
- Stars already there when the app opens light up once, as before, and do not grow.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: how a new star enters and how five gold become a purple — [delta](specs/app-navigation/spec.md).

## Decisions

- Motion and timing: the owner's pick from the mockup (scale 0 → 1.1 → 1 over 600 ms; the gathering over 450 ms after it).
- The bar draws only the count memory holds; the stars move as elements come and go: a CSS animation as a star is put in, Replicant's `:replicant/unmounting` for those that leave. No state says a star is new. A finished lesson shows before its record reaches memory, and a motion keyed to it ran on the old count: the bar blinked. Leaving the lesson does not cut the motion, and a lesson from another device moves too: owner.
- The stars the app opens on come with the row, and only light up: the row's first 1.5 s start every motion as far back as it is long; a style query on the row's animated `--app-shell-stars-open` ends that. A `var()` of an animated property in `animation` is void by the CSS spec, so the timing itself cannot be a variable.
- The row lays the stars from the right, newest first in the markup: a star that comes is put before those that go, so it stands right of them.
