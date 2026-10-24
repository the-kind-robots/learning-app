(async () => {
  const parseWordIds = (html) => {
    const doc = new DOMParser().parseFromString(html, "text/html");
    return [...doc.querySelectorAll('[id^="word-"]')]
      .map((node) => node.id.replace(/^word-/, ""))
      .filter((id) => /^[0-9a-f-]{8,}$/.test(id));
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

  const addWord = async (value, translation) => {
    const body = new URLSearchParams({ value, translation });
    const resp = await fetch("/words", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
      body: body.toString()
    });
    return resp.status;
  };

  const statuses = [];
  statuses.push(await addWord("Lampe", "лампа"));
  statuses.push(await addWord("Tisch", "стол"));

  return { deleted: ids.length, statuses };
})()
