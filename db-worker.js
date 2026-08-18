// Same VFS as production: sqlite-wasm 3.53.1 with installOpfsSAHPoolVfs,
// byte-identical sqlite3.js and sqlite3.wasm.
//
// The database is generated locally on first run so the public repository
// stays free of a 37 MB binary; on every later start the pool file is only
// reopened.

const TARGET_BYTES   = 37 * 1024 * 1024;
const POOL_FILE      = "/control.sqlite";
const BATCH_ROWS     = 128;
const BLOB_BYTES     = 8000;
const READS_PER_TICK = 50;

let db = null;
let queries = 0;

const say = (text) => self.postMessage({ type: "status", text });

function dbBytes() {
  return db.selectValue("PRAGMA page_count") * db.selectValue("PRAGMA page_size");
}

function fill() {
  db.exec("CREATE TABLE IF NOT EXISTS blob_rows(id INTEGER PRIMARY KEY, payload BLOB)");
  while (dbBytes() < TARGET_BYTES) {
    db.exec("BEGIN");
    db.exec(`WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < ${BATCH_ROWS})
             INSERT INTO blob_rows(payload) SELECT randomblob(${BLOB_BYTES}) FROM c`);
    db.exec("COMMIT");
    say(`filling ${(dbBytes() / 1048576).toFixed(1)} of ${(TARGET_BYTES / 1048576).toFixed(0)} MiB`);
  }
}

async function open() {
  say("loading wasm");
  importScripts("sqlite3.js");
  const sqlite3 = await sqlite3InitModule();

  say("installing opfs-sahpool");
  await sqlite3.installOpfsSAHPoolVfs({ name: "opfs-sahpool", initialCapacity: 4 });

  say("opening db");
  db = new sqlite3.oo1.DB({ filename: POOL_FILE, vfs: "opfs-sahpool" });
  fill();

  const rows = db.selectValue("SELECT count(*) FROM blob_rows");
  const mib  = (dbBytes() / 1048576).toFixed(1);
  say(`db ${mib} MiB · ${rows} rows`);

  setInterval(() => {
    const t0 = performance.now();
    for (let i = 0; i < READS_PER_TICK; i++) {
      db.selectValue("SELECT length(payload) FROM blob_rows WHERE id = ?",
                     1 + Math.floor(Math.random() * rows));
      queries++;
    }
    self.postMessage({ type: "tick", queries, rows, mib,
                       lastMs: (performance.now() - t0).toFixed(1) });
  }, 1000);
}

// One holder of the pool at a time. The lock is released when this worker is
// terminated, so a reload waits for the previous document instead of racing
// it for the OPFS access handles.
say("waiting for storage lock");
navigator.locks.request("sah-control-opfs", async () => {
  try {
    await open();
  } catch (err) {
    self.postMessage({ type: "error", text: String(err) });
  }
  await new Promise(() => {});
});
