## Why

On desktop, successful add-word submit resets the form but does not return focus to the German word input. This slows down repeated entry because keyboard users and fast typists must manually refocus the field after every submit.

## What Changes

- Restore focus to the German word input after a successful home add-word submit on desktop-style pointer devices.
- Keep mobile behavior unchanged so the soft keyboard is not reopened unexpectedly after submit.
- Verify the behavior in the real browser flow used for repeated word entry.

## Capabilities

### New Capabilities
- `home-add-form-focus`: Defines post-submit focus behavior for the home add-word flow.

### Modified Capabilities
- _(none)_

## Impact

- Affected code: `src/client/views/home.cljs`.
- Affected behavior: repeated word entry on the home screen after successful submit.
- Validation focus: desktop browser submit/focus loop and a sanity check that mobile-like behavior is not forced.
