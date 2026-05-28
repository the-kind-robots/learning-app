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

// Hold the OPFS SAH pool lock for the worker's entire lifetime.
// `ifAvailable: true` makes a second tab fail fast instead of hanging:
// it gets null and we report a clear "another tab open" error.
async function withSahPoolLock(initFn) {
  return navigator.locks.request(
    "sprecha-sqlite-opfs-sahpool",
    { ifAvailable: true },
    async (lock) => {
      if (lock === null) {
        self.postMessage({
          type:    "error",
          code:    "another-tab-open",
          message: "Приложение уже открыто в другой вкладке. Закройте её и обновите страницу."
        });
        return;
      }
      try {
        await initFn();
        self.postMessage({ type: "ready" });
      } catch (err) {
        self.postMessage({ type: "error", message: String(err) });
        return;
      }
      // Keep the lock alive for the rest of the worker's life;
      // the browser releases it automatically when the worker terminates.
      await new Promise(() => {});
    });
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
  } else {
    if (telemetry) self.postMessage({ type: "phase", phase: "cache-hit", status: "ok", durationMs: 0, hash12 });
  }

  await measure("cleanup", () => pool.reduceCapacity(1));

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

withSahPoolLock(() => measure("init", init));
