# Card Switcher — UX Specification

A Duolingo-flavored, Safari-tabs-inspired view for managing word sets in Sprecha. Lives behind the grid icon in the top-right of the home screen.

---

## App shell

A single persistent header sits at the top of the viewport on every view:

- **Left:** "Sprecha" wordmark
- **Right:** an icon button that morphs between a 2×2 grid (when on home) and an × (when on switcher)

The header never animates between views. Both home and switcher live in the content area below the header. The card-zoom transition expands a tapped card into this content area only — the header is never covered.

---

## The main (default) card

The first card is **special** and immutable in the following ways:

- **Cannot be deleted.** No × button is ever rendered on it.
- **Cannot be moved out of first position** during drag-reorder.
- **Has a fixed name: "Всё подряд"** ("everything"). The name input is disabled on this card so the user cannot rename it — it's the catch-all bucket for users who don't engage with sets at all.
- **Always present.** Users who don't care about organising their words can dump everything into "Всё подряд" and never see the switcher.

This is the escape hatch: the app works as a single-list flashcard tool by default. Card sets are an opt-in feature for users who want them.

---

## Home view

```
┌─────────────────────────────┐
│  Sprecha              ⊞     │  ← persistent header
├─────────────────────────────┤
│                             │
│     [   Set name   ]        │  ← editable inline; hidden entirely
│                             │     on the main card (its name lives
│   ┌─────────────────────┐   │     only on the card preview itself)
│   │ Добавить слово  ⋯   │   │
│   │ [word]  →  [trans]  │   │
│   │ [ ДОБАВИТЬ ]        │   │
│   └─────────────────────┘   │
│                             │
├─────────────────────────────┤
│       [ НАЧАТЬ УРОК ]       │  ← sticky footer; uses --accent
└─────────────────────────────┘
```

- **Set name** is the H1 input. Typing renames the set live. On non-main cards it shows the name (or `Без названия` placeholder); on the main card the input is **not rendered at all** — "Всё подряд" appears only on the card preview in the switcher, never as a heading on home. The home view for the main set jumps straight to the add-word card.
- **Add-word card** is the primary surface: two inputs (German → Russian) and a submit button.
- **Footer** holds the lesson button. Always pinned; never scrolls.

The full list of words for a set is **intentionally not on the home view**. It appears as a content preview inside each switcher card (see "Card content" below). A "Список слов" pill button in the add-card header is reserved for that flow.

---

## Switcher view

A grid of cards. **No title, no edit button, no instructions.** The only chrome is the persistent header above (with × in place of the grid icon).

### Card anatomy

```
╭────────────╮  ← thin 2px border in palette color
│ ┌────────┐ │     (3px + soft glow for active card)
│ │ name   │ │
│ └────────┘ │
│ ● word  ru │
│ ● word  ru │
│ ● ...      │
╰────────────╯
```

- **Aspect ratio:** 9 : 16 (mobile-shaped, regardless of viewport)
- **Border:** 2px solid, color cycled from `green / blue / orange / pink / purple / yellow / red / teal`
- **Active card:** 3px border + soft outer glow in the same color
- **Content:** name (centered, bold, no frame) at top + word list below (see "Card content" section above). Rendered natively at the card's pixel size — no scaling.

### New-set tile

The last cell in the grid. Distinguished by:

- **No background fill, no shadow, no 3D button effect.** It is *not* a button-as-card; it is a *placeholder* that turns into a card when used.
- **Dashed outline** drawn via SVG (long, even dashes — 7-unit dash, 4-unit gap on a 9:16 viewBox) so dashes look intentional at any size.
- **Pale-green `+` glyph** centered, no surround.

Tapping it creates a new empty set, activates it, and returns to home so the user can immediately name it.

---

## Card interactions

### Idle state
| Gesture | Action |
|---|---|
| Tap | Zoom into the card; open that set on home |
| Long-press (500 ms) | Enter editing state on this card |

### Editing state (per-card, only one card at a time)
| Gesture | Action |
|---|---|
| Tap × button | Delete the card (animated removal). Exits editing. |
| Drag (hold + move after long-press, or pointer-down + move on an already-editing card) | Reorder. Card lifts (scale 1.04, raised z-index). Other cards reflow as the dragged card crosses their bounds. The main card is locked to position 0 — neither can it move nor can other cards drop into its slot. |
| Any tap not on × (on this card, on another card, on the background, or `Escape`) | Exit editing. **Does not open the card.** |
| Scroll (pointer-down + move >10 px **before** the 500 ms timer fires) | Long-press is cancelled; normal scroll behaviour. |

### Why this state machine
- **Long-press as the gate** prevents accidental deletes and accidental reorders. A normal tap is always "open this set."
- **Single active editing card** keeps the UI calm — no app-wide "jiggle mode."
- **Any-tap-exits** matches the iOS pattern: editing feels like a temporary spotlight, not a sticky mode.

---

## Naming

Set names are changed in exactly one place: the **inline H1 input on the home page**. There is intentionally no rename UI on the switcher — the switcher is for navigating and arranging cards, not for editing their content.

Names accept any text, including emoji, mixed scripts, or empty strings. The system never validates or normalizes the name. Empty non-main cards show a `Без названия` placeholder until typed into.

The main card is the exception: its name is fixed to `Всё подряд` and the input is **not rendered at all** on the home view. The name appears only on the card preview in the switcher — never as a heading on home.

---

## Transitions

| From → To | Treatment |
|---|---|
| Home → Switcher (tap grid icon) | **Planned: View Transitions API zoom-out.** The home view shrinks to its card position in the switcher grid. Implementation: tag the home content area and the corresponding card with the same `view-transition-name`, then call `document.startViewTransition(() => setView('switcher'))`. The browser handles the morph and crossfade. Falls back to an instant swap on browsers without the API. |
| Switcher → Home (tap card) | Zoom: the tapped card animates from its grid position to fill the entire content area (`top: 0, left: 0, width: 100%, height: 100%`), border radius eases to 0, then fades out as the home view renders underneath. ~420 ms. **Planned migration to View Transitions API** using the same paired `view-transition-name` technique. |
| Switcher → Home (tap × in header) | Same zoom-out as "tap grid icon" — animates from full home view back into the *active* card's position. |

The zoom is bounded to the content area, never the header. This reinforces that the header is the *frame*, the content is the *stage*.

### Why View Transitions API

- One source of truth for both directions. The current implementation maintains a separate `zoom-overlay` div with cloned styling; the API eliminates that duplication.
- Browser-native morphing handles position, size, and crossfade in a single declarative call — no manual `requestAnimationFrame` chains, no per-property transition timing.
- Free aspect-ratio interpolation: a 9:16 card morphing into a full-bleed home view "just works" because each side of the transition has its own real layout.
- Works with any DOM update inside the transition callback — state changes, full re-renders, async data fetches — without the developer choreographing them.

---

## Responsive behaviour

- **<600 px (mobile):** shell fills the viewport; 2-column grid; standard padding.
- **600–899 px (tablet):** shell capped at 480 px and centred with a card border around it (the app reads as a "device" on big tablets).
- **900–1199 px (small desktop):** shell fills viewport; switcher has half-card-width side padding so the grid still feels intentionally inset.
- **≥1200 px (desktop):** switcher side padding equals one full card width + gap. The grid auto-fills the inner column. Home view content (title, form, lesson button) stays in a 680 px column centred in the shell — never stretches to full width.

---


## What this design intentionally avoids

- **Themed cards.** No emoji-per-set, no auto-detected category icons, no decorative header bands.
- **A grid jiggle.** Only the actively-edited card reacts; the rest stay still.
- **A "Sets" title** or any switcher chrome. The view is just cards.
- **Word-count badges, streak indicators, XP, mascots.** None of these appear on the switcher. The grid's job is to find a set, not to gamify it.
- **A confirmation modal on delete.** The × is hidden behind long-press, which is intent enough. (Future: undo toast.)
- **Multi-place renaming.** The H1 input is the single source of truth.
