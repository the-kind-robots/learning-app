## Why

A link to the app shared in Telegram, WhatsApp and other messengers showed only the page title: the page head had no Open Graph tags (#818).

## What Changes

- Open Graph and `twitter:card` tags in the page the server sends, naming one of four pictures, `/link-preview/<language>.png` — the behaviour is the [delta](specs/link-preview/spec.md).
- `scripts/link-preview.mjs` draws the pictures; the headers it uses are screenshots in `scripts/link-preview/`.

## Capabilities

### New Capabilities
- `link-preview`: what a messenger shows for a link to the app — [delta](specs/link-preview/spec.md).

### Modified Capabilities

## Decisions

The owner picked each part from mockups (#818, the canvas of preview mockups).

- A picture per language, Russian as the fallback: messengers that unfurl a link on the sender's phone (WhatsApp, iMessage, Signal) are expected to name the phone's language; one that unfurls on its own servers (Telegram) may name none, and Russian is what most learners read. What each messenger sends is checked on production after the deploy.
- The name is the Latin «Sprecha» in every language; no logo; no description — the picture says it. A light leaf ground keeps the night screen visible; the two phones stand side by side, whole.
- The statistics are drawn for a learner of about 5000 words, most of them learned, lessons some days many, some few, in the app's colours and layout but for the histogram, fuller than the app's (the owner's pick). The generator reads the colours from the app's token sheets and the words from the dictionaries, the month and the weekdays from `Intl`; the colour formulas, the groups' bounds and the layout are copied from the app, so a change there needs the generator changed by hand. The headers are screenshots of a development build at eight lessons today, as the calendar's last day shows, so a five-fold star shows beside single ones; the build's own marks are hidden; the Russian one reads «Шпреха».
- A language counts only when its picture is there: a dictionary added without a picture falls back to Russian rather than to a broken card. The server reads the pictures once, at start.
- The public address is the one the invite links use; a blank setting now counts as none for both — the invite links' was a relative link before.
- The picture's address carries `?v=` and the first eight hex digits of the file's sha256 ([delta](specs/link-preview/spec.md); the owner's pick). It reaches only messengers that fetch the page anew: Telegram keeps a link's preview until its `WebpageBot` is asked to refresh it.
- `og:url` is the site's root for every page: one preview for the whole app (the owner's pick).
- `og:site_name`, `og:locale` and `og:image:alt`, which the issue listed, are left out: the name is already the title, the language is the request's, and the preview carries no text beside the picture's own.
- The pictures sit in `resources/public/link-preview/`, outside the directories the service worker precaches.
