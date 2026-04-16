(async () => {
  window.confirm = () => true;

  const row = document.querySelector(".word-item");
  if (!row) return { error: "no-word-item" };

  row.querySelector(".word-item__display")?.click();

  await new Promise((resolve) => setTimeout(resolve, 300));

  const del = document.querySelector(".word-item__delete");
  if (!del) return { error: "no-delete-button" };

  del.click();

  return { clicked: true };
})()
