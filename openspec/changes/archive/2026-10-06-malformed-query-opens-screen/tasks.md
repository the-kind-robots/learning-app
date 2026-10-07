## 1. A malformed query

- [x] 1.1 Browser spec: `/home?x=%` opens home at `/home`, and the themes control navigates
- [x] 1.2 `:app/router` drops a query that does not decode before the router reads the address
