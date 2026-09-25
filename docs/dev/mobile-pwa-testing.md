# Mobile PWA Dev Access (Cloudflare Tunnel)

Use this guide to open your local dev app on a real mobile device over HTTPS.

The tunnel is a public front for the dev stand from `development-setup.md`: Cloudflare terminates TLS at `<name>.dev.sprecha.de` and forwards into the same local Nginx that serves `sprecha.localhost`.

## Team model

- Use one tunnel per developer.
- Use one hostname per developer, for example `<name>.dev.sprecha.de`.
- Do not share tunnel tokens between developers.

This avoids routing conflicts and makes ownership clear.

## Prerequisites

1. The stand is up on your machine — `infra/scripts/install-dev-stand.sh`,
   see [development-setup.md](development-setup.md).
2. `sprecha.de` DNS is managed by Cloudflare.
3. `cloudflared` installed.

macOS:

```bash
brew install cloudflared
```

Ubuntu/Debian:

```bash
sudo mkdir -p --mode=0755 /usr/share/keyrings
curl -fsSL https://pkg.cloudflare.com/cloudflare-public-v2.gpg | sudo tee /usr/share/keyrings/cloudflare-public-v2.gpg >/dev/null
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-public-v2.gpg] https://pkg.cloudflare.com/cloudflared any main' | sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt-get update
sudo apt-get install -y cloudflared
```

## One-time setup (per developer)

1. Create a Cloudflare Tunnel in Zero Trust.
2. Add a public hostname route:
   - Hostname: `<name>.dev.sprecha.de`
   - Service type: `HTTP`
   - Service URL: `127.0.0.1:80`
   - Origin settings:
     - `HTTP Host Header = sprecha.localhost`
3. Install service with your tunnel token:

```bash
sudo cloudflared service install <TUNNEL_TOKEN>
sudo systemctl enable --now cloudflared
```

4. Verify connector is healthy:

```bash
systemctl status cloudflared
journalctl -u cloudflared -f
```

## Over the tailnet

The stand is also reachable on the machine's Tailscale name,
`https://<machine>.<tailnet>.ts.net`. Tailscale terminates TLS and proxies to
the same local nginx, so the origin is https and therefore a secure context —
the service worker registers and the app installs to the home screen exactly as
on the tunnel hostname. The phone needs Tailscale switched on; nothing off the
tailnet can reach the name at all.

Two commands, once per machine:

```bash
sudo tailscale set --operator=$USER
sudo tailscale serve --bg --https=443 http://127.0.0.1:80
```

Worth knowing before switching: browser storage is per origin. Opening the app
on the tailnet name is a fresh install — an empty local database that
repopulates by sync, and a home-screen icon that has to be added again.

## Running the stand

The stand is two systemd **user** units, `infra/development/etc/systemd/user/`:
`learning-app-dev-watch.service` runs `shadow-cljs watch app`,
`learning-app-dev-backend.service` runs the backend on 8083. Both restart on
failure and both work from `~/Projects/learning-app`; a checkout elsewhere gets
a drop-in (`systemctl --user edit <unit>`) rather than an edited unit.

Installing them, enabling them and turning on lingering — without which the
user manager stops at logout and takes both units with it — is the setup
script's job, and it is the only copy of those commands:

```bash
infra/scripts/install-dev-stand.sh
```

Run it again after pulling a changed unit: it notices the difference, shows it,
and reinstalls only after you say so.

```bash
systemctl --user status learning-app-dev-watch learning-app-dev-backend
systemctl --user restart learning-app-dev-backend
systemctl --user stop learning-app-dev-watch learning-app-dev-backend
journalctl --user -u learning-app-dev-watch -f
```

The logs are the journal — the compile output the watch used to print into a
terminal is `journalctl --user -u learning-app-dev-watch`.

## Daily workflow

1. Start local app stack (the units above, if they are not already running).
2. Ensure `cloudflared` service is running.
3. Open `https://<name>.dev.sprecha.de` on phone.
4. Install to home screen and test from PWA icon.

The devtools socket connects to the page's own origin and to nothing else
(`dev/cljs/dev/devtools_socket.cljs`, a `:devtools :preloads` entry). There is
no setting, so where hot reload works follows from which origins nginx proxies
`/shadow-cljs/` for: the machine's tailnet address and `sprecha.localhost`,
yes; the public dev host, no — nginx refuses a request the tunnel forwarded,
and that refusal is what keeps the watch off the public internet.

So on the public name the app works and only hot reload is missing. What it
looks like: the shadow-cljs client keeps retrying and shows its reconnect
banner over the page, forever. That is the arrangement, not a fault. Open the
app on the tailnet name — Tailscale switched on — when you want hot reload.

nginx proxies `/shadow-cljs/` to port 9630, so the watch holding that port is
the only one the phone can reach, and only one watch per machine can hold it.
`shadow-cljs.edn` pins `:http {:port 9630 :strict true}`: a second watch now
dies with `BindException: Address already in use` instead of drifting to the
next free port. Without the pin it drifted silently, and the symptom was the
socket URL looking right while nginx answered 502 on `/shadow-cljs/` and the
page reconnected forever. Seen that? Check nothing else is running a watch
(`ss -ltnp | grep 9630`), then restart the stand's.

## Getting the new build

The service worker never activates on its own (#278): a new build waits
until asked, and on Android a swiped-away PWA is not a closed tab, so without
asking it would wait for the system to kill the process. The app asks for it:

- «Обновить» appears in the top-right row when a new worker is waiting; tap
  it. Every open tab of the origin reloads onto the new build. Same in a
  development build: a watch writes a new worker on every recompile, and
  taking each one would reload every open page and lose the hot reload.
- The check for a new build runs every time the app comes back to the
  foreground, so bring the phone back and look at the row.
- In a development build a tap on the build mark does the same without the
  row: it checks, activates and reloads.

## Reading the trace after a freeze

A development build (`shadow-cljs watch`/`compile`, never `release`) keeps a
ring of the last 500 events on the page, so when a screen goes dead you can
read what happened last without DevTools having been attached at the time.

- Live: `window.__trace()` in the console (remote inspect, or a CDP eval).
- After a reload or a crash: `JSON.parse(localStorage.getItem('sprecha:trace'))`.
  The ring is mirrored into localStorage on every error-class entry and on
  every visibility change, so the copy is at most one quiet stretch old.

Each entry is `{t, kind, data}` with `t` in ms since page start
(`performance.now()`). Kinds:

| kind | data | meaning |
|---|---|---|
| `action` | `{action}` | a Nexus action was dispatched (name only, no payload) |
| `effect-start` / `effect-done` | `{effect}` / `{effect, ms}` | an effect began / settled, with its duration (async effects are timed to the promise); saves and event plumbing are not traced |
| `effect-failed` | `{effect, error}` | an async effect rejected |
| `dispatch-error` | `{phase, source, message, stack}` | Nexus caught a throw in an action, an effect, or the render a save triggered — errors the dispatcher otherwise drops silently |
| `pointerdown`, `pointerup`, `pointercancel`, `click`, `contextmenu` | `{target, card, pointerType}` | each step of a tap inside `.switcher`; `card` is the `data-collection-id` of the card under the finger (`main` for the main card). A lost tap reads as `pointerdown` → `pointercancel` (the browser took the gesture) or `pointerdown` → `pointerup` with no `click` and a `render` in between (the node was replaced) |
| `touchcancel` | `{target}` | the browser cancelled a touch anywhere on the page |
| `pointercancel` (extra fields) | `{movedPx, scrollDelta}` | how far the finger went and how much the page scrolled since the pointerdown — the tap recovery's inputs |
| `tap-recovered` | `{movedPx, scrollDelta}` | a cancelled tap that did not move was taken as a tap and its action dispatched |
| `render` | `{ms}` | one Replicant render and its duration |
| `longtask` | `{start, duration}` | the main thread was blocked for ≥ 50 ms |
| `error` / `unhandledrejection` | `{message, stack, …}` | uncaught script error / rejected promise |
| `console-error` | `{message, stack}` | anything logged with `console.error`, which includes Replicant's "Caught exception during rendering" |
| `visibilitychange`, `pageshow`, `pagehide`, `freeze`, `resume` | `{visibility}` | page lifecycle |

Getting it off the phone: a development build puts the export first in the
top-right row of shell actions. Tap it and the share sheet opens with
`sprecha-trace-<timestamp>.json`; choose Telegram or mail and send it. Where
the share sheet cannot take a file (desktop Chrome) the JSON goes to the
clipboard and the page says «Трасса скопирована»; where there is no clipboard
either, a prompt shows the JSON for selecting by hand. The file carries a
`header` (build, time, URL, user agent, visibility, storage estimate), `live`
(the ring as it was at the tap) and `stored` (the last localStorage mirror,
which is what survives a reload).

The grey line in the middle of the top bar is which build the page loaded:
the short commit of the checkout it compiled from, a `+` when that tree had
uncommitted changes, and `DD.MM HH:MM` of the compile. The trace's `build`
header carries the same string. It changes when a rebuild lands, so it is how
you tell whether the phone is on the new build or still on the old one — and
tapping it is what brings the newest build (`openspec/specs/service-worker-update`).
The red D at the end of the word mark is a letter of the name, not a control.

Typical read: find the last `click` or `tap-recovered`; if no `action` follows it the tap never
reached Nexus; if an `action` follows but no `effect-done` for
`:effect/load-collections`, the read never came back; a `longtask` or a
`console-error` in between says why.

## Quick troubleshooting

- `Error 1033`: connector is not connected. Check `systemctl status cloudflared` and logs.
- `502`/`525`: route origin settings are wrong. Recheck `No TLS Verify` and `HTTP Host Header`.
- PWA seems stale: clear site data/service worker and reinstall from the dev hostname.
