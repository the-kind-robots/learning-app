# 0025. Interface text comes from an m1p dictionary

- Status: accepted
- Date: 2026-10-25

## Context

The interface's words were inline Russian in the views, in the arguments views hand to effects (a prompt, a confirm, a status message) and in the server's splash (#472). Another language would have meant editing every view, and the page named no language.

## Decision

A view writes `[:i18n :key params]` wherever text goes, in markup and in the actions it attaches. The shell is passed through `i18n/interpolate` between the view and `replicant/render`; the server's layout does the same for its body. The words are data: one EDN dictionary per language, `src/shared/i18n/<language>.edn`, prepared and looked up with m1p and read when the code compiles. Text with values uses `[:fn/str "… {{:param}} …"]`. `<html lang>` names the language.

`i18n/interpolate` is our walk, not `m1p/interpolate`: a vector or map without text is returned as it came, so a render copies only the branches that hold text.

## Consequences

- A view knows keys, not words; a new language is a new dictionary file, not new code.
- Every render walks the whole shell once more. On a word list of 200 rows (development build) the walk costs 2–7 ms a render; `m1p/interpolate` cost 12–18 ms in the same page.
- A key missing from the dictionary shows as m1p's error vector instead of text; nothing checks the keys at compile time.
- Presenters and domain code stay free of interface text; a view still picks the key.
