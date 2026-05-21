(function () {
  const rect = (node) => {
    if (!node) return null;

    const box = node.getBoundingClientRect();
    return {
      top: box.top,
      left: box.left,
      width: box.width,
      height: box.height
    };
  };

  const sample = (phase) => {
    const cta = [...document.querySelectorAll("button")]
      .find((node) => node.textContent.trim() === "Добавить слово");
    const empty = document.querySelector(".vocabulary__empty-state");
    const state = document.querySelector("#vocabulary-state")?.className || null;

    window.__layoutProbe = window.__layoutProbe || [];
    window.__layoutProbe.push({
      phase,
      ts: performance.now(),
      state,
      cta: rect(cta),
      empty: rect(empty)
    });
  };

  window.__layoutProbe = [];
  sample("armed");

  document.body.addEventListener("htmx:beforeSwap", () => sample("beforeSwap"));
  document.body.addEventListener("htmx:afterSwap", () => sample("afterSwap"));
  document.body.addEventListener("htmx:afterSettle", () => {
    sample("afterSettle");
    requestAnimationFrame(() => sample("raf+1"));
    requestAnimationFrame(() => requestAnimationFrame(() => sample("raf+2")));
  });

  "armed";
})()
