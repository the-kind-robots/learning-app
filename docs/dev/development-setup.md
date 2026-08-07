# Development Setup

This guide describes how to set up and run the Learning App in development mode.

## Prerequisites

### Required Software

1. **Java 21+** - Required for Clojure
2. **Clojure CLI** - [Installation guide](https://clojure.org/guides/install_clojure)
3. **Node.js 18+** - Required for shadow-cljs and npm dependencies
4. **SQLite3** - Usually pre-installed on macOS/Linux
5. **CouchDB** - Document database for data synchronization

### Installing CouchDB

**macOS (Homebrew):**
```bash
brew install couchdb
```

**Ubuntu/Debian:**
```bash
sudo apt-get install couchdb
```

**Other platforms:** See the [official CouchDB installation guide](https://docs.couchdb.org/en/stable/install/index.html)

#### CouchDB Admin Setup (Required)

CouchDB requires an admin account before starting. Uncomment the default admin line in the config file:

**macOS (Homebrew):**

Edit `/opt/homebrew/opt/couchdb/etc/local.ini` and uncomment the admin line in the `[admins]` section:

```ini
[admins]
admin = 3434
```

CouchDB will hash the password on first start.

> **Note:** The password `3434` matches the default in `src/shared/db.cljc`. For production, use a strong password.

#### Dictionary Database Setup

After CouchDB is running, create the `dictionary-db` database and configure it for public read access:

```bash
curl -X PUT http://admin:3434@localhost:5984/dictionary-db
curl -X PUT http://admin:3434@localhost:5984/dictionary-db/_security \
  -H "Content-Type: application/json" \
  -d '{"admins":{"names":["admin"],"roles":[]},"members":{"names":[],"roles":[]}}'
```

This matches the production configuration: anyone can read, only the admin can write.

## Setup Steps

### 1. Install npm dependencies

```bash
npm install
```

### 2. Initialize the SQLite database

```bash
sqlite3 app.db < initial-setup.sql
```

### 3. Start CouchDB

Run directly in a terminal:
```bash
couchdb
```

> **Note:** `brew services start couchdb` may fail with I/O errors. Running `couchdb` directly is more reliable.

Verify CouchDB is running:
```bash
curl http://localhost:5984/
```

CouchDB web interface (Fauxton) is available at: http://localhost:5984/_utils/

### 3.5 Generate + import dictionary (before running the server)

Do this after CouchDB is up and before starting the backend:

```bash
clj -M:dictionary
clj -T:build dictionary-import
COUCHDB_URL=http://localhost:5984 COUCHDB_PASS=3434 java -jar target/dictionary-import.jar --input-dir resources/dictionary
```

Add `--reset` only if you need to replace an existing `dictionary-db`.

### 4. Start shadow-cljs (ClojureScript compiler)

```bash
npx shadow-cljs watch app
```

Wait for the message: `Build completed`

This starts:
- Dev server at http://localhost:9630
- nREPL server on port 4444

### 5. Start the backend server

In a separate terminal:

```bash
clj -M:dev -m core
```

The backend server runs at: **http://127.0.0.1:8083/**

> **Note:** Use `127.0.0.1` instead of `localhost` in Safari to avoid cookie issues.

## Creating a Test User

Run this command to create a test user (login: `test`, password: `test123`):

```bash
clj -M:dev -e '
(require (quote [buddy.hashers :as hashers]))
(require (quote [next.jdbc :as jdbc]))
(let [db {:dbtype "sqlite" :dbname "app.db"}
      password-hash (hashers/derive "test123" {:alg :argon2id})]
  (jdbc/execute! db ["INSERT INTO users (name, password) VALUES (?, ?)" "test" password-hash]))
'
```

## Local Domain Setup (sprecha.localhost)

For full functionality including CouchDB sync, set up the local domain with Nginx proxy.

The host is `sprecha.localhost` on plain http: browsers resolve `*.localhost` to loopback themselves and treat it as a secure context, so Secure cookies and the service worker work with no certificates and no hosts-file entry. To test from a phone, a Cloudflare tunnel feeds this same Nginx under `<name>.dev.sprecha.de` — see [mobile-pwa-testing.md](mobile-pwa-testing.md).

### 1. Install Nginx

```bash
# macOS
brew install nginx
# Ubuntu/WSL
sudo apt-get install -y nginx
```

### 2. Hosts entry for CLI tools (optional)

Browsers need nothing. `curl` and other CLI tools resolve through the OS, which may not know `*.localhost`:

```bash
sudo sh -c 'echo "127.0.0.1 sprecha.localhost" >> /etc/hosts'
```

### 3. Configure CouchDB proxy authentication

```bash
cp infra/development/opt/couchdb/etc/local.d/00-proxy-auth.ini /opt/homebrew/opt/couchdb/etc/local.d/
```

Restart CouchDB after copying the config.

### 4. Install the Nginx config

```bash
# macOS
cp infra/development/etc/nginx/sites-available/sprecha.localhost.conf /opt/homebrew/etc/nginx/servers/
# Ubuntu/WSL
sudo cp infra/development/etc/nginx/sites-available/sprecha.localhost.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/sprecha.localhost.conf /etc/nginx/sites-enabled/
```

### 5. Start Nginx

```bash
# macOS
brew services restart nginx
# Ubuntu/WSL
sudo systemctl reload nginx
```

### 6. Access the app

Open **http://sprecha.localhost/** in your browser.

## Summary

| Service | URL | Port |
|---------|-----|------|
| App (with Nginx) | http://sprecha.localhost/ | 80 |
| Backend | http://127.0.0.1:8083/ | 8083 |
| shadow-cljs | http://localhost:9630/ | 9630 |
| nREPL | - | 4444 |
| CouchDB | http://localhost:5984/ | 5984 |
| CouchDB UI (Fauxton) | http://localhost:5984/_utils/ | 5984 |

## Troubleshooting

### "Can't connect to server" in Safari
Use `http://127.0.0.1:8083/` instead of `http://localhost:8083/`

### Service Worker registration fails
Clear browser cache and reload. The `Service-Worker-Allowed` header is required for the sw.js file.

### CouchDB connection errors in logs
These errors appear when CouchDB is not running. Start CouchDB with `brew services start couchdb` or `couchdb`.

### "no such table: sessions" error
Run the database initialization: `sqlite3 app.db < initial-setup.sql`

## Related Guides

- Mobile PWA testing on a real phone via cloudflared: `docs/dev/mobile-pwa-testing.md`
