# 0028. One walk puts the text in and expands the aliases

- Status: accepted
- Date: 2026-11-11

## Context

Views write their blocks as Replicant aliases (`defalias`): `[word-list-item item]` instead of `(word-list-item item)`, so the markup reads as tags with attributes (#823). The text is put in before the render: `render!` handed the shell through `i18n/interpolate` (ADR-0025). An alias that Replicant expands inside the render returns its `[:i18n …]` too late for that.

Handing the language to the aliases in `:alias-data`, as Replicant's i18n tutorial does, fails on a language switch: Replicant skips a node whose hiccup equals the last render's, and the alias data is no part of an alias's hiccup. Only a development build invalidates the tree when it changes, and only under a vector root. An alias whose attributes did not change would keep the last language, and a remount on the switch would close the settings the language was checked in. So the aliases are expanded before the render.

On a shell of 200 word rows (JVM, mean of 1000 runs) the interpolation alone took 0.77–1.23 ms, `replicant.alias/expand` followed by it 3.1–5.8 ms, and one walk that puts the text in and expands the aliases 0.82 ms.

## Decision

`markup/walk` walks markup once and asks a function what each node is replaced by; it puts the answer in the node's place and walks it in turn. It knows neither text nor aliases. `i18n/text` is the text of one `[:i18n …]`, and `i18n/interpolate` is the walk with it alone, as the server's layout uses it. `application.markup/renderable` is the walk with both: a text node gets its text, a registered alias its markup, with the alias's `:replicant/key` on the root as Replicant's own expansion puts it. `render!` hands the shell through it, and Replicant receives markup without aliases.

## Consequences

- An alias writes its text as a view does, `[:i18n …]`, and knows no language; `i18n` knows no alias.
- No second walk: the shell is walked once, as before.
- A key in a list goes on the alias where it stands, `[tile (assoc props :replicant/key (:tile/key props))]`; a key inside an alias that remounts its markup when it changes stays on the root.
- Markup that holds aliases and is not rendered through `render!` goes through `renderable` too, as the shared statistics picture will once its view writes aliases.
