## Why

The operator's invite mail is a plain list of links, and the page a link opens can come up unstyled until a reload: the page links the app's `/css/styles.css`, which the service worker answers from its cache, and after a deploy that cache can still be the last one's (#727).

## Changes

- The mail carries an HTML part next to the plain-text one: the request, the Grant and Decline buttons, then the note. Spec: [identity-provisioning](specs/identity-provisioning/spec.md).
- The page carries its styles inline and no longer links the app's stylesheet.
- The Worker passes an `html` field on to `send`. An older Worker ignores it and sends the text part alone.

## Capabilities

### Modified Capabilities
- `identity-provisioning`: the mail has an HTML part; the operator's page carries its own styles.

## Decisions

- Deciding stays two steps, link then button: mail scanners open links. That is the reason for the page, not a choice of looks.
- The buttons come before the note in both parts: mail clients turn addresses in text into links, so a note above the buttons could pass off a "Grant" link of its own.
- The page's styles are inlined from a resource outside `resources/public`. A file under `/css/` would come from the service worker's cache, and keeping a backend page out of the worker means changing the worker.
- The mail's light colours are inline on each element, and the dark ones sit in a `<style>`: some clients drop a `<style>`, and those keep the light mail.
