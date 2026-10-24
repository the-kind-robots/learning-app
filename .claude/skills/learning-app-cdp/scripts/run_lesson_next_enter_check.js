(async () => {
  const waitFor = async (pred, timeout = 5000) => {
    const start = performance.now();
    while (performance.now() - start < timeout) {
      const value = pred();
      if (value) return value;
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    return null;
  };

  if (location.pathname !== "/lesson") {
    document.querySelector("#home-lesson-footer .home__lesson-button")?.click();
    await waitFor(() => location.pathname === "/lesson");
  }

  if (!document.querySelector("#lesson-next")) {
    const answer = document.querySelector("#lesson-answer");
    answer.value = "__wrong__";
    answer.dispatchEvent(new Event("input", { bubbles: true }));
    answer.form.requestSubmit();
    await waitFor(() => document.querySelector("#lesson-next"));
  }

  const nextBtn = document.querySelector("#lesson-next");
  const beforeFooter = document.querySelector("#lesson-footer")?.innerText || "";
  nextBtn.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
  const changed = await waitFor(() => {
    const footer = document.querySelector("#lesson-footer")?.innerText || "";
    return footer !== beforeFooter;
  });

  return {
    result: "next-enter",
    activeIdBefore: document.activeElement?.id || null,
    changed: !!changed,
    pathAfter: location.pathname,
    footerAfter: document.querySelector("#lesson-footer")?.innerText || null
  };
})()
