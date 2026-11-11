## Why

The manifest spoke one language (#816 made it Russian), while the interface speaks Russian, English, French or Turkish. A learner who reads only Turkish would have met the install offer, the label under the icon and the shortcuts in Russian (#820).

## What Changes

- The server answers `/manifest.json` and `/dev-manifest.json` itself, in the language it picks for the page (`page-language`: the first `Accept-Language` tag, else English, else Russian). The static files are gone.
- Name, description, shortcut names and screenshot labels come from the dictionaries (`:install/*`, `:lesson/title`); the label under the icon is the word mark, `:shell/word-mark`.
- Screenshots per language under `/screenshots/<language>/`, taken on seeded test data in that language.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-installation`: the install offer, the label and the shortcuts speak the page's language — [delta](specs/app-installation/spec.md).

## Decisions

- The learner's pick of language lives in their user-db, which the server does not read, so the manifest follows the system's language, as the server's page does. A learner who picked another language in the settings installs an app labelled in the system's.
- One `id`, `/home`, for every language: the browser keeps an installed app the same app whatever language its manifest came in.
- `Vary: Accept-Language` and `no-cache` on the answer, so no cache hands one learner's manifest to another.
- The worker reads the manifests from the network first and keeps the last good answer, as it does the dictionary's manifest: a copy served cache-first would keep the language the worker installed in until the next deploy. They stay in the precache too, as the first copy: the browser reads the manifest before the worker controls the first page, and a new worker's activate drops the old bucket with its copy, so without it a first visit and the first open after a deploy would go offline with no manifest. A computer offline still offers installing (the owner's pick: worth the extra path in the worker).
