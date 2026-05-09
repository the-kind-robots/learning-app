let db = null;

async function init() {
  const params = new URL(self.location.href).searchParams;
  const dir = params.get("sqlite3.dir");
  importScripts(dir ? `${dir}/sqlite3.js` : "sqlite3.js");

  const sqlite3 = await sqlite3InitModule();
  const pool = await sqlite3.installOpfsSAHPoolVfs({ name: "opfs-sahpool", initialCapacity: 6 });

  const manifest = await fetch("/dictionary/manifest").then(r => r.json());
  const hash12 = manifest.hash.slice(0, 12);
  const poolFile = `/dict.${hash12}.sqlite`;

  if (!pool.getFileNames().includes(poolFile)) {
    const buffer = await fetch(`/dictionary/${manifest.filename}`).then(r => r.arrayBuffer());
    await pool.importDb(poolFile, new Uint8Array(buffer));
  }

  db = new sqlite3.oo1.DB({ filename: poolFile, vfs: "opfs-sahpool" });
}

self.addEventListener("message", e => {
  const msg = e.data;
  try {
    self.postMessage({ id: msg.id, result: db.exec(msg) });
  } catch (err) {
    self.postMessage({ id: msg.id, error: String(err) });
  }
});

init()
  .then(() => self.postMessage({ type: "ready" }))
  .catch(err => self.postMessage({ type: "error", message: String(err) }));
