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

  const openDb = () =>
    new Promise((resolve, reject) => {
      const req = indexedDB.open("_pouch_device-db");
      req.onerror = () => reject(req.error);
      req.onsuccess = () => resolve(req.result);
    });

  const readStoreKey = (db, storeName, key) =>
    new Promise((resolve, reject) => {
      const tx = db.transaction(storeName, "readonly");
      const req = tx.objectStore(storeName).get(key);
      req.onerror = () => reject(req.error);
      req.onsuccess = () => resolve(req.result);
    });

  const readLessonDoc = async () => {
    const db = await openDb();
    const meta = await readStoreKey(db, "document-store", "lesson");
    if (!meta) return null;
    const seqDoc = await readStoreKey(db, "by-sequence", meta.seq);
    return seqDoc || null;
  };

  if (location.pathname !== "/lesson") {
    document.querySelector("#home-lesson-footer .home__lesson-button")?.click();
    await waitFor(() => location.pathname === "/lesson");
  }

  for (let step = 0; step < 12; step += 1) {
    const finishBtn = document.querySelector("#lesson-finish");
    if (finishBtn) {
      finishBtn.dispatchEvent(new KeyboardEvent("keydown", { key: " ", bubbles: true }));
      const home = await waitFor(() => location.pathname === "/home" && document.querySelector("#home-add-panel"), 4000);
      return {
        result: "finish-space",
        step,
        activeIdBefore: document.activeElement?.id || null,
        pathAfter: location.pathname,
        homeReached: !!home
      };
    }

    const nextBtn = document.querySelector("#lesson-next");
    if (nextBtn) {
      const beforeText = document.querySelector("#lesson-footer")?.innerText || "";
      nextBtn.form.requestSubmit();
      await waitFor(() => {
        const footerText = document.querySelector("#lesson-footer")?.innerText || "";
        return location.pathname === "/home" || footerText !== beforeText;
      });
      continue;
    }

    const answer = document.querySelector("#lesson-answer");
    if (answer) {
      const lesson = await readLessonDoc();
      const correctAnswer = lesson?.current_trial?.answer;
      if (!correctAnswer) {
        return { result: "missing-answer", step, lesson };
      }
      const beforeText = document.querySelector("#lesson-footer")?.innerText || "";
      answer.value = correctAnswer;
      answer.dispatchEvent(new Event("input", { bubbles: true }));
      answer.form.requestSubmit();
      await waitFor(() => {
        const footerText = document.querySelector("#lesson-footer")?.innerText || "";
        return footerText !== beforeText;
      });
      continue;
    }

    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  return {
    result: "not-finished",
    path: location.pathname,
    footer: document.querySelector("#lesson-footer")?.innerText || null
  };
})()
