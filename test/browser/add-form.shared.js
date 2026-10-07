const { expect } = require('./fixtures');

// Adding a word through the home add form, shared by every spec that needs
// words on the way to what it tests (#512).
//
// A word with a dictionary translation prefills the empty translation field
// once its suggestions arrive. Playwright's `fill` is two steps: focus and
// select, then insert. When the prefill lands between them the selection is
// gone and the insert appends: «дом» becomes «домдом». The field counts as
// typed after that insert, so the prefill does not come again and a second
// fill replaces the value.

async function fillTranslation(page, translation) {
  const field = page.getByLabel('Перевод (русский)');
  await expect(async () => {
    await field.fill(translation);
    await expect(field).toHaveValue(translation, { timeout: 500 });
  }).toPass();
}

// Returns once the form has cleared itself, which is the app saying the write
// landed; a second add before that races the first.
async function addWord(page, value, translation) {
  await page.getByLabel('Слово (немецкий)').fill(value);
  await fillTranslation(page, translation);
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
}

module.exports = { addWord, fillTranslation };
