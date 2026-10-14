## 1. Backend

- [x] 1.1 The prompt carries the sentences to avoid; a candidate equal to one of them (normalised) is refused and retried
- [x] 1.2 `examples/regenerate!` adds the cached example of the subject to the list, generates, stores nothing, joins no run
- [x] 1.3 `POST /api/examples` reads the JSON body, same session check, blank-word 400 and status table as GET
- [x] 1.4 Tests: prompt, rejected repeat, POST 200 / 401 / 400, cache row unchanged
