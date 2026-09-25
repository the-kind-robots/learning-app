## 1. Cache only good responses (#315)

- [x] 1.1 Browser spec: an asset answering 500 once is fetched from the network next time and then cached as 200
- [x] 1.2 Browser spec: a manifest answering 500 leaves the cached manifest in place
- [x] 1.3 `cacheFirst` and `networkFirstCached` write only `ok` same-origin (`basic`) responses

## 2. Keep the request's URL (#299)

- [x] 2.1 Browser spec: after a controlled reload, online and offline, the dictionary worker reports its `cache-hit` phase
- [x] 2.2 `cacheFirst` looks up and stores by path only, and re-wraps every hit in a new `Response`, which takes the request's URL
