## Why

The interface speaks Russian to everyone. The learner's own language — ru, en, tr or fr — becomes a setting the learner picks once and every paired device follows (#473). The interface dictionary of #472 made the words switchable; nothing yet says which language to switch to.

## Changes

- A settings document in user-db holds the learner's language and replicates with the rest of the learner's data.
- **BREAKING** The word mark «Sprecha» opens the settings instead of going home; they list the languages, and checking one re-renders the interface in it and stores it.
- A language is there when its `i18n/<language>.edn` is: the dictionaries are found when the code compiles, and each names its language. A key a dictionary lacks reads in Russian.
- Without a stored choice the language follows the system; a German system is asked once, when there is a choice.
- The server's page declares the language the request's `Accept-Language` gives by the same rule, and the client sets `<html lang>` to the learner's language once it renders.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `interface-language`: the page declares the learner's language rather than always Russian; the learner picks the language; the language without a choice follows the system.
- `data-model`: a settings document stores the learner's language.
- `app-navigation`: the word mark no longer leads home.

## Decisions

- The setting lives in a user-db document, so it replicates and works offline — over the backend's unused `user_settings` table, which needs an endpoint and the network.
- The document is written only by an explicit choice — over writing the system's language at first run, which a freshly paired device could then replicate over the learner's choice.
- A conflict between two devices' choices is left to PouchDB's deterministic winner — over a last-writer-wins resolver: every device converges on the same winner already.
- The settings open from the word mark (the owner's call) — over a language button beside sync, and over the sync menu, which only a device with an account sees; every screen but home has its own close mark.
- The languages are the dictionary files (the owner's call), so English, Turkish and French arrive with #476–#478 as one file each — over listing ru, en, tr, fr in the code and speaking Russian under the three without a file. With Russian alone the settings still open and list it.
- The dictionaries are inlined by a macro when the code compiles: the release jar carries no source files. shadow-cljs keeps `i18n` out of its cache (`:cache-blockers`), so a watch build sees a new file once anything recompiles; the dev backend drops its AOT-cached `i18n` classes at each start (`dev/user.clj`), so it compiles them from the files of the moment.
