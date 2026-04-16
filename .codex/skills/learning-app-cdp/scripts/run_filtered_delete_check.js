(async () => {
  const input = document.querySelector('.vocabulary__search input[name="search"]');
  if (!input) return { error: "no-search-input" };

  input.value = "Lam";
  input.dispatchEvent(new Event("input", { bubbles: true }));

  await new Promise((resolve) => setTimeout(resolve, 900));

  const row = document.querySelector(".word-item");
  if (!row) return { error: "no-filtered-word" };

  window.confirm = () => true;
  row.querySelector(".word-item__display")?.click();

  await new Promise((resolve) => setTimeout(resolve, 300));

  const del = document.querySelector(".word-item__delete");
  if (!del) return { error: "no-delete-button" };

  del.click();

  await new Promise((resolve) => setTimeout(resolve, 1200));

  const appText = document.querySelector("#app")?.innerText || "";
  const searchStillVisible = !!document.querySelector('.vocabulary__search input[name="search"]');
  const emptyText = document.querySelector(".vocabulary__empty-state-text")?.textContent?.trim() || null;
  const hintText = document.querySelector(".vocabulary__empty-state-hint")?.textContent?.trim() || null;
  const ctaVisible = !![...document.querySelectorAll("button")]
    .find((node) => node.textContent.trim() === "Добавить слово");
  const wordCount = document.querySelectorAll(".word-item").length;

  return {
    searchStillVisible,
    emptyText,
    hintText,
    ctaVisible,
    wordCount,
    appText: appText.slice(0, 220)
  };
})()
