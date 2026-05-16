let db = null;

const telemetry = new URL(self.location.href).searchParams.has("telemetry");

async function measure(phase, fn, extras) {
  if (!telemetry) return fn();
  const t0 = performance.now();
  try {
    const result = await fn();
    const msg = { type: "phase", phase, status: "ok", durationMs: Math.round(performance.now() - t0) };
    if (extras) Object.assign(msg, extras(result));
    self.postMessage(msg);
    return result;
  } catch (err) {
    self.postMessage({ type: "phase", phase, status: "error",
                       durationMs: Math.round(performance.now() - t0), reason: String(err) });
    throw err;
  }
}

async function init() {
  const params = new URL(self.location.href).searchParams;
  const dir = params.get("sqlite3.dir");
  importScripts(dir ? `${dir}/sqlite3.js` : "sqlite3.js");

  const sqlite3 = await measure("wasm-init", () => sqlite3InitModule());
  const pool    = await measure("pool-install", () =>
    sqlite3.installOpfsSAHPoolVfs({ name: "opfs-sahpool", initialCapacity: 3 }));

  const manifest = await measure("manifest", () =>
    fetch("/dictionary/manifest").then(r => r.json()));
  const hash12   = manifest.hash.slice(0, 12);
  const poolFile = `/dict.${hash12}.sqlite`;

  if (!pool.getFileNames().includes(poolFile)) {
    const buffer = await measure("download",
      () => fetch(`/dictionary/${manifest.filename}`).then(r => r.arrayBuffer()),
      ab => ({ bytes: ab.byteLength }));
    await measure("import", () => pool.importDb(poolFile, new Uint8Array(buffer)));
  } else {
    if (telemetry) self.postMessage({ type: "phase", phase: "cache-hit", status: "ok", durationMs: 0, hash12 });
  }

  await measure("cleanup", async () => {
    for (const name of pool.getFileNames()) {
      if (name !== poolFile && name.startsWith("/dict.") && name.endsWith(".sqlite"))
        pool.unlink(name);
    }
    await pool.reduceCapacity(1);
  });

  db = await measure("db-open", () =>
    new sqlite3.oo1.DB({ filename: poolFile, vfs: "opfs-sahpool" }));
}

self.addEventListener("message", e => {
  const msg = e.data;
  try {
    self.postMessage({ id: msg.id, result: db.exec(msg) });
  } catch (err) {
    self.postMessage({ id: msg.id, error: String(err) });
  }
});

measure("init", init)
  .then(() => self.postMessage({ type: "ready" }))
  .catch(err => self.postMessage({ type: "error", message: String(err) }));
