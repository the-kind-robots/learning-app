# Development Setup

## Bring the stand up

One command, from the checkout:

```bash
infra/scripts/install-dev-stand.sh
```

It checks every piece the stand needs, installs what this repository carries —
the nginx vhost and the snippet it includes, the CouchDB proxy-auth config and
the two databases, the systemd user units — and names what it cannot do. It is
safe to re-run: a machine that is already correct is told so and left alone, a
file that differs is shown before it is replaced, and a step needing root shows
the exact command and asks. Answering no to everything is the dry run: the
report is complete and nothing is touched.

The script is the only copy of the list. When a piece of the stand changes, it
changes there; this document does not repeat it.

Then open **http://sprecha.localhost/**.

## What the script cannot do

It installs no toolchain and holds no credentials. These stay yours:

- **Java, Clojure CLI, Node, CouchDB, nginx** — below.
- **A CouchDB admin account** — below. Without it the script cannot create the
  databases.
- **An account in the app** — «Your account», below.
- **A Cloudflare tunnel token** and **a Tailscale login**, for reaching the
  stand from a phone — [mobile-pwa-testing.md](mobile-pwa-testing.md).

The script names each of these when it finds it missing, with the section that
covers it.

## Prerequisites

1. **Java 21+** — required for Clojure
2. **Clojure CLI** — [installation guide](https://clojure.org/guides/install_clojure)
3. **Node.js 18+** — required for shadow-cljs and npm dependencies
4. **nginx** — `sudo apt-get install -y nginx`
5. **CouchDB** — below

### Installing CouchDB

**Ubuntu/Debian:**
```bash
sudo apt-get install couchdb
```

**macOS (Homebrew):**
```bash
brew install couchdb
```

**Other platforms:** see the [official installation guide](https://docs.couchdb.org/en/stable/install/index.html).

### CouchDB admin account

CouchDB needs an admin before it will start. Uncomment the default admin line
in the `[admins]` section of the config — `/opt/couchdb/etc/local.d/10-admins.ini`
on Debian, `/opt/homebrew/opt/couchdb/etc/local.ini` under Homebrew:

```ini
[admins]
admin = 3434
```

CouchDB hashes the password on first start.

> The password `3434` matches the dev fallback in `lib/db/src/db.cljc`
> (`LEARNING_APP__COUCHDB_PASSWORD` overrides it). A deployed instance uses a
> real one. The script reads `COUCHDB_USER` and `COUCHDB_PASS` if yours differ.

## Your account

Identity is one bearer token per account (ADR-0006); there is no name and no
password. Mint yourself an invite and open the URL it prints:

```bash
clj -M:dev -m core mint-invite
```

It touches SQLite only — no CouchDB credentials, no server. The server database
itself needs no preparation: the backend migrates it on boot, creating it when
it is not there.

## Reference

| Service              | URL                            | Port |
|----------------------|--------------------------------|------|
| App (through nginx)  | http://sprecha.localhost/      | 80   |
| Backend              | http://127.0.0.1:8083/         | 8083 |
| shadow-cljs          | http://localhost:9630/         | 9630 |
| nREPL                | —                              | 4444 |
| CouchDB              | http://localhost:5984/         | 5984 |
| CouchDB UI (Fauxton) | http://localhost:5984/_utils/  | 5984 |

The stand is plain http on purpose: browsers resolve `*.localhost` to loopback
themselves and treat it as a secure context, so Secure cookies and the service
worker work with no certificates and no hosts entry. Both ways in from a phone
terminate TLS elsewhere and proxy here —
[mobile-pwa-testing.md](mobile-pwa-testing.md).

Running the stand day to day — status, logs, restarts — is the same document's
«Running the stand».

### Hosts entry for CLI tools (optional)

Browsers need nothing. `curl` and other CLI tools resolve through the OS, which
may not know `*.localhost`:

```bash
sudo sh -c 'echo "127.0.0.1 sprecha.localhost" >> /etc/hosts'
```

## Troubleshooting

### The script says a step is left
Re-run it. Each step reports what it found; the ones it cannot do are marked
`manual` and carry the section that covers them.

### "Can't connect to server" in Safari
Use `http://127.0.0.1:8083/` rather than `http://localhost:8083/`.

### Service worker registration fails
Clear the site's data and reload. `sw.js` needs the `Service-Worker-Allowed`
header, which the backend sets when it serves the file.

### CouchDB connection errors in the log
CouchDB is not running. `systemctl status couchdb`, or `couchdb` in a terminal.

## Related guides

- Phone testing over a Cloudflare tunnel or the tailnet: [mobile-pwa-testing.md](mobile-pwa-testing.md)
- The units and vhost themselves: [../../infra/README.md](../../infra/README.md)
