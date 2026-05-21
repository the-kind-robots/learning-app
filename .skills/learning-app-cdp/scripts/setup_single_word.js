(async () => {
  const parseWordIds = (html) => {
    const doc = new DOMParser().parseFromString(html, "text/html");
    return [...doc.querySelectorAll('[id^="word-"]')]
      .map((node) => node.id.replace(/^word-/, ""))
      .filter(Boolean);
  };

  const listHtml = await fetch("/words", { credentials: "same-origin" }).then((r) => r.text());
  const ids = parseWordIds(listHtml);

  for (const id of ids) {
    await fetch(`/words/${id}`, {
      method: "DELETE",
      credentials: "same-origin",
      headers: { "HX-Target": "word-list" }
    });
  }

  const word = "Lampe";
  const body = new URLSearchParams({ value: word, translation: "лампа" });

  const addResponse = await fetch("/words", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
    body: body.toString()
  });

  const afterHtml = await fetch("/words", { credentials: "same-origin" }).then((r) => r.text());

  return {
    deleted: ids.length,
    addStatus: addResponse.status,
    afterIds: parseWordIds(afterHtml),
    word
  };
})()
