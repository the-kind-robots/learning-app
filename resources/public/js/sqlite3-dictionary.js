// The dictionary belongs to the tab being typed into.
//
// `opfs-sahpool` takes exclusive sync access handles over its pool files, so
// one browsing context at a time can have the database open. Which one should
// it be? The foreground tab: on screen and holding the keyboard. A tab takes
// the `sqlite-opfs-sahpool` lock when it comes to the foreground and gives it
// back when it leaves.
//
// A *turn* is one stretch of holding that lock, from the grant to the release.
// Everything this file does with the database happens inside a turn; between
// turns it has no database at all.
//
// Coming to the foreground and having the database are not the same instant,
// and a query asked in the gap is answered with what the tab has at that
// instant: nothing. Nothing is queued and nothing is replayed — see `request`.
// A tab's first turn has to load the engine and install the pool (~94 ms); a
// later one waits for the tab in front to hand the lock back (~14 ms). Both
// are shorter than the 100 ms debounce in front of the query, so a warm start
// has almost always finished before the first query arrives.
//
// On screen alone is not enough. Two tabs can be visible at the same time —
// split screen, two windows side by side — and only one of them is being
// typed into; `document.hidden` is false for both, so it would hand the
// database to whichever tab happened to take the lock first rather than the
// one the user is working in. Focus is what distinguishes them, so the page
// reports `visibilityState === "visible" && hasFocus()`, and this file is
// told the answer as one boolean without being told how it was reached.
//
// The visibility half is not a preference, it is what Android leaves us.
// Measured with two tabs and one query every 10 s while the tab holding the
// database sat in the background: 12 ms, 14, 17, 22, 31, 31 — and from 60 s
// on, nothing at all, 14 consecutive timeouts until it was brought back to
// the front. A backgrounded tab is frozen whole, worker and message delivery
// together, so anything that leaves the database behind a backgrounded tab
// has built a dictionary that stops answering after a minute.
//
// `visibilitychange` and `blur` both fire the moment the tab stops being the
// one in front, and the freeze lands 50-60 s later, so the handover has three
// orders of magnitude more time than it needs. If a tab is killed outright
// instead, the browser releases its lock and the next tab takes over — the
// same path as closing one.
//
// When no tab is in the foreground — the whole browser window is behind
// something else — no tab holds the database. There is nobody to ask, and the
// first tab to come back takes it in ~14 ms.

const SAH_POOL_LOCK = "sqlite-opfs-sahpool";

const workerParams = new URL(self.location.href).searchParams;
const telemetry = workerParams.has("telemetry");

// The engine, loaded once for the worker's life. The pool and the open
// database come and go with the lock; this does not.
let sqlite3 = null;
let engine = null;

// Installed on this tab's first turn and kept, paused, between turns:
// `unpauseVfs` costs a third of a fresh install and asks the disk for nothing
// the pool already knows.
let pool = null;
let poolFile = null;

// Set only while this tab holds the lock. Its presence is what "this tab has
// the dictionary" means everywhere below.
let db = null;

// What the page last said about itself: on screen and holding the keyboard.
// Nothing is taken before it says anything, so a tab that boots in the
// background never takes a database it would be frozen holding.
let foreground = false;

// The lock: `queued` while waiting for a turn, `yieldTurn` set while holding
// one — calling it ends the turn, which is how the lock is given back.
let queued = false;
let yieldTurn = null;

// Where this worker is, as the message its page is sent. `loading` covers both
// "starting up" and "waiting for another tab to finish", because from the
// page's side they are the same thing: no dictionary here, so no completions
// from here either.
let lifecycle = { type: "loading" };

let broadcast = () => {};


// Every phase the page's instrumentation reports, startup and per-turn alike.
// Nothing is accumulated here — the page keeps the list, this only reports.
async function measure(phase, fn, extras) {
  if (!telemetry) return fn();
  const t0 = performance.now();
  try {
    const result = await fn();
    const msg = { type: "phase", phase, status: "ok", durationMs: Math.round(performance.now() - t0) };
    if (extras) Object.assign(msg, extras(result));
    broadcast(msg);
    return result;
  } catch (err) {
    broadcast({ type: "phase", phase, status: "error",
                durationMs: Math.round(performance.now() - t0), reason: String(err) });
    throw err;
  }
}


// Loaded on this tab's first turn, not at startup: a tab that never comes to
// the front never pays for the engine, and a phone with several tabs open is
// exactly where that matters. Kept afterwards, so later turns cost only the
// pool and the database.
function loadEngine() {
  if (engine) return engine;
  engine = (async () => {
    const dir = workerParams.get("sqlite3.dir");
    importScripts(dir ? `${dir}/sqlite3.js` : "sqlite3.js");
    sqlite3 = await measure("wasm-init", () => sqlite3InitModule());
  })();
  return engine;
}


async function installPool() {
  pool = await measure("pool-install", () =>
    sqlite3.installOpfsSAHPoolVfs({ name: "opfs-sahpool", initialCapacity: 3 }));

  const manifest = await measure("manifest", () =>
    fetch("/dictionary/manifest").then(r => r.json()));
  const hash12 = manifest.hash.slice(0, 12);
  poolFile = `/dict.${hash12}.sqlite`;

  // Unlink stale versions first so their handles become available for import.
  for (const name of pool.getFileNames()) {
    if (name !== poolFile && name.startsWith("/dict.") && name.endsWith(".sqlite"))
      pool.unlink(name);
  }

  if (!pool.getFileNames().includes(poolFile)) {
    const buffer = await measure("download",
      () => fetch(`/dictionary/${manifest.filename}`).then(r => r.arrayBuffer()),
      ab => ({ bytes: ab.byteLength }));
    await measure("import", () => pool.importDb(poolFile, new Uint8Array(buffer)));
  } else if (telemetry) {
    broadcast({ type: "phase", phase: "cache-hit", status: "ok", durationMs: 0, hash12 });
  }

  await measure("cleanup", () => pool.reduceCapacity(1));
}


async function open() {
  await loadEngine();
  if (pool) await measure("unpause", () => pool.unpauseVfs());
  else await measure("init", installPool);
  db = await measure("db-open", () =>
    new sqlite3.oo1.DB({ filename: poolFile, vfs: "opfs-sahpool" }));
}


// Hands the file handles back so the next tab can take them. `pauseVfs` throws
// while any connection is open, so the close is not optional.
//
// `db` is dropped before anything that can throw, and the throw is swallowed,
// because `db` is what tells the rest of this file that the lock is ours. If
// `close()` rejected, the lock callback would reject with it: the lock would
// go to the next tab while `request()` kept calling `exec` on a pool this tab
// no longer owns. Recovery is the next turn, not a retry — `unpauseVfs` on a
// pool that was never paused is a no-op — and under telemetry `measure` has
// already reported the failed `pause`, so nothing is lost by not rethrowing.
async function close() {
  const closing = db;
  db = null;
  try {
    await measure("pause", () => {
      closing.close();
      pool.pauseVfs();
    });
  } catch (ignored) { /* the turn ends either way */ }
}


function answer(db, msg) {
  try {
    return { id: msg.id, result: db.exec(msg) };
  } catch (err) {
    return { id: msg.id, error: String(err) };
  }
}


// What to say to one request, given what this tab has. An empty result is the
// same shape a real empty answer has — `resultRows` is an array — so the page
// reads it as a prefix with no completions. An error is not flattened into
// one: a dictionary this tab could not open has to stay distinguishable from
// a word it does not contain (#312).
function respond(db, lifecycle, msg) {
  if (db) return answer(db, msg);
  if (lifecycle.type === "error") return { id: msg.id, error: lifecycle.message };
  return { id: msg.id, result: [] };
}


// The response to one request, decided from what this tab has at that instant.
// Nothing is queued and nothing is replayed: without a turn the answer is an
// empty list, and being asked again takes another keystroke.
//
// What that loses is bounded by the debounce in front of it. Taking the lock
// back from the tab in front costs ~14 ms and a first turn ~94 ms, against a
// 100 ms trailing debounce and a keystroke every 120-180 ms, so on a warm
// start the turn has begun before the query is sent. A cold start is the one
// window a user actually sees: the dictionary is still downloading, and every
// keystroke until it lands gets an empty list. Holding those queries buys
// that one case and costs a mechanism to do it; the empty answer is accepted
// for now, and #312 is where "the dictionary is not here" stops looking like
// "no such word".
function request(msg) {
  return respond(db, lifecycle, msg);
}


// Where this worker is, told to the page. It feeds the page's log and its
// instrumentation and nothing here waits on it, so `loading` is as reportable
// as `ready` and `error`.
function report(state) {
  lifecycle = state;
  broadcast(state);
}


function acquire() {
  if (db || queued || yieldTurn) return;
  queued = true;
  navigator.locks.request(SAH_POOL_LOCK, async () => {
    queued = false;
    // Left the foreground while waiting its turn. Returning frees the lock
    // for the next tab at once; nothing was opened, so nothing is closed.
    if (!foreground) return;
    try {
      await open();
    } catch (err) {
      // A pool left unpaused would poison every other tab, so it goes back
      // before the lock does, whatever went wrong above. `db` is dropped
      // first for the same reason as in `close()`.
      try {
        const opened = db;
        db = null;
        if (opened) opened.close();
        if (pool && !pool.isPaused()) pool.pauseVfs();
      } catch (ignored) { /* nothing better to try */ }
      // Released by returning: this tab could not open the database, and the
      // next one — online where this one was not, say — deserves its turn.
      report({ type: "error", message: String(err) });
      return;
    }
    // Left the foreground while the database was opening. Nothing has been
    // promised to the page yet, so it just goes straight back.
    if (foreground) {
      report({ type: "ready" });
      // Assigned with no await in between, so no report from the page can
      // slip past between the check above and this handle existing.
      await new Promise((done) => { yieldTurn = done; });
      yieldTurn = null;
    }
    await close();
    // Back to waiting: queries asked from here on are answered empty, not
    // from a database this tab no longer has.
    report({ type: "loading" });
    // In front again already — the tab came back while this turn was being
    // wound up. The request queues behind this callback and is granted as
    // soon as it returns, so nothing is lost to the gap.
    if (foreground) acquire();
  });
}


// The page reporting on itself: on screen and holding the keyboard, as one
// boolean. Called with the truth at startup and whenever either half of it
// changes, so the first thing this worker hears is whether it may take the
// dictionary at all.
//
// Repeated reports of the same value cost nothing, which matters because the
// page has three events feeding this and they overlap: leaving for another
// application fires `blur` and `visibilitychange` both.
function pageIsForeground(next) {
  if (next === foreground) return;
  foreground = next;
  if (foreground) acquire();
  // Given back at once rather than after a grace period. Focus flickers
  // more than visibility does — a click in the address bar, a glance at
  // devtools — and a grace timer would be the wrong answer to it twice
  // over: in a backgrounded tab timers are throttled, and the one thing that
  // must not happen is being frozen while still holding the lock. A round
  // trip costs ~14 ms of worker time and never blocks the tab that has the
  // keyboard. A tab queued but not yet granted needs nothing here — the
  // callback checks `foreground` when its turn comes.
  else if (yieldTurn) yieldTurn();
}


function start(emit) {
  broadcast = emit;
}


self.dictionary = { pageIsForeground, request, start };
