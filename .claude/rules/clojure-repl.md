---
paths:
  - "**/*.clj"
  - "**/*.cljs"
  - "**/*.cljc"
---

# Clojure REPL Workflow

## Evaluating code

Prefer heredoc with single-quoted delimiter to avoid shell escaping bugs (`swap!`, `reset!` etc. get mangled otherwise):

```bash
clj-nrepl-eval -p <PORT> <<'EOF'
(swap! my-atom inc)
EOF
```

Simple expressions without `!` can use inline args: `clj-nrepl-eval -p <PORT> "(+ 1 2)"`

Discover running nREPL servers: `clj-nrepl-eval --discover-ports`

## After editing a file

Always verify by requiring the namespace and calling the affected function with representative args:

```bash
clj-nrepl-eval -p <PORT> <<'EOF'
(require 'my.namespace :reload)
(my.namespace/edited-fn sample-arg)
EOF
```

`require` returning `nil` = compiles. Calling the function catches runtime errors (wrong arity, broken destructuring, nil punching) that compilation misses.

## Unbalanced delimiters

Do NOT attempt to fix mismatched parens/brackets manually. Run `clj-paren-repair <file>` instead. If the tool fails, report to the user.
