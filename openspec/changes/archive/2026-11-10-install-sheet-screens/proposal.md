## Why

Chrome offers to install the app in a short dialog: an icon, an English name, the address. The web manifest can make the offer show the app itself, and the installed app fit the phone's launcher (#816).

## Changes

- The manifests name the app and describe it in Russian, and carry five phone screenshots: the lesson, a word with its example, the statistics, the themes, the lesson at night.
- A maskable icon on a white ground, the scribbled «Ü» enlarged and kept inside the safe zone.
- Two shortcuts on a long press of the icon: «Урок» and «Слова».
- An explicit `id`, equal to the identity the browser derived from `start_url`.

## Capabilities

### New Capabilities
- `app-installation`: how the app offers itself for installing and looks once installed — [delta](specs/app-installation/spec.md).

### Modified Capabilities

## Decisions

The owner picked each look from mockups (#816, comments).

- Plain screenshots, no captions or frames.
- No wide screenshots. The app is a narrow column, and a wide frame of it is mostly empty. Chrome on a computer keeps its short dialog.
- Five screenshots: Chrome shows at most five on a phone. The home screen, the least telling of the six in the mockup, is left out.
- `short_name` turns Russian too: «Шпреха», the word mark the Russian interface shows. It is the label under the icon.
- Screenshots live in `/screenshots/`, outside the directories the service worker precaches: the install offer is made online, and a learner should not download them with the app.
- They are taken from a development build on seeded test data, with the build's red D hidden: a release build draws no D and exposes no database to seed.
- The development manifest mirrors the production one, with the red-D icons, so the dev stand shows the same offer.
