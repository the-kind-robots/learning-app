## 1. Tests

- [x] 1.1 Browser spec `test/browser/lesson-review.spec.js`: the panel's colour passes through the heights between, the button stays; after the lesson «ЗАКОНЧИТЬ» keeps its distance below the edge and never fades; `npm run test:catalog`

## 2. Design

- [x] 2.1 Canvas with two choreographies (text rides the edge / text stays in place); the owner chose the second

## 3. Screen

- [x] 3.1 `<page-panel>`: the height of the child shown in `--page-panel-height`, `easing` while the page changes
- [x] 3.2 View: the strip's answer one per page, leaving by unmounting attributes; the strip leaving inert at once
- [x] 3.3 CSS: the panel's `::before` paints the colour; transitions of its height and colour, of the current panel's parts and of the strip's answer; reduced motion

## 4. Verify

- [x] 4.1 Frame-by-frame filmstrips on a phone viewport: tiles <-> past word, green current <-> past word; every painted frame recorded over 30 changes shows no white flash
- [x] 4.2 Lesson browser specs pass (desktop and mobile); release bundle renames nothing
- [ ] 4.3 The flicker the owner saw, and the swipe on a real phone, are the owner's check
