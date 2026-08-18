// Control PWA for issue #360: OPFS SAH-pool storage and nothing else.
// The database lives in a worker; the page paints two counters only, so that
// a frozen system surface stays distinguishable from a frozen app by eye.
//
// The document title mirrors the status line: the browser process reports it
// over CDP even when the renderer is busy, which is how this app is checked.

const stateEl   = document.getElementById("state");
const queriesEl = document.getElementById("queries");
const aliveEl   = document.getElementById("alive");

const started = Date.now();
const aliveSeconds = () => Math.round((Date.now() - started) / 1000);

let status = "worker starting";

function show(text) {
  status = text;
  stateEl.textContent = text;
}

setInterval(() => {
  aliveEl.textContent = aliveSeconds();
  document.title = `SAH ${aliveSeconds()}s · ${status}`;
}, 1000);

show(status);

const worker = new Worker("db-worker.js");

worker.addEventListener("message", (e) => {
  const msg = e.data;
  if (msg.type === "status") show(msg.text);
  if (msg.type === "error")  show("error: " + msg.text);
  if (msg.type === "tick") {
    queriesEl.textContent = msg.queries;
    show(`db ${msg.mib} MiB · ${msg.rows} rows · read ${msg.lastMs} ms`);
  }
});

// Let go of the OPFS access handles before the next document claims them.
addEventListener("pagehide", () => worker.terminate());

if ("serviceWorker" in navigator)
  navigator.serviceWorker.register("sw.js", { scope: "./" });
