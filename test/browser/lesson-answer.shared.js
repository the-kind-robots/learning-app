const { expect } = require('@playwright/test');

// Lesson setup shared by the desktop and the phone answer specs.
//
// The examples backend needs an external API, so the example document is
// seeded straight into the app's device-db through the dev-build globals —
// the same layer the app itself uses (see test/browser/README.md).

async function addWord(page, value, translation) {
  await page.getByLabel('Слово (немецкий)').fill(value);
  await page.getByLabel('Перевод (русский)').fill(translation);
  await page.getByRole('button', { name: 'ДОБАВИТЬ' }).click();
  await expect(page.getByLabel('Слово (немецкий)')).toHaveValue('');
}

// Seeds at the engine level (`db`, the PouchDB wrapper): a raw document into
// the database that holds examples. The app's own layers (`db.pouch` and the
// adapters) need the `dbs` map that only `init!` in `main` builds, so a spec
// does not reach for them — see test/browser/README.md.
async function seedExample(page) {
  await page.evaluate(async () => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    const found = await db.find(db.use('user-db'), toClj({ selector: { type: 'vocab' } }));
    const wordId = cljs.core.get(cljs.core.first(cljs.core.get(found, kw('docs'))), kw('_id'));
    await db.insert(db.use('device-db'), toClj({
      'type': 'example',
      'word-id': wordId,
      'word': 'der Hund',
      'value': 'Der Hund schläft im Garten.',
      'translation': 'Пёс спит в саду.',
      'created-at': new Date().toISOString(),
      'structure': [
        { usedForm: 'Hund', dictionaryForm: 'der Hund', translation: 'пёс', wordIndex: 1 },
        { usedForm: 'Garten', dictionaryForm: 'der Garten', translation: 'сад', wordIndex: 4 },
      ],
    }));
  });
}

// Answers the word trial, advances, and answers the example trial with
// `exampleAnswer`. Returns with the revealed answer on screen.
async function playToExampleReveal(page, exampleAnswer) {
  await page.goto('/lesson');
  await expect(page.locator('.lesson__prompt')).toBeVisible();
  await page.locator('#lesson-answer').fill('der Hund');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  await page.getByRole('button', { name: 'ДАЛЕЕ' }).click();
  await expect(page.locator('.lesson__instruction')).toContainText('предложение');
  await page.locator('#lesson-answer').fill(exampleAnswer);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
}

async function setUpLesson(page, exampleAnswer) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await seedExample(page);
  await playToExampleReveal(page, exampleAnswer);
}

const token = (page, index) => page.locator(`.lesson__answer-token[data-word-index="${index}"]`);

// The revealed answer's rendered width, and the width of the same text set
// as plain text in the same block — a sibling copy with the same class, so
// the same font and size, removed again at once.
async function answerWidths(page) {
  return page.evaluate(() => {
    const body = document.querySelector('.lesson__answer-body:has(.lesson__answer-token)');
    const width = (el) => {
      const range = document.createRange();
      range.selectNodeContents(el);
      return range.getBoundingClientRect().width;
    };
    const plain = body.cloneNode(false);
    plain.textContent = body.textContent;
    body.after(plain);
    const widths = { hinted: width(body), plain: width(plain) };
    plain.remove();
    return widths;
  });
}

// A hinted word adds no width of its own: at rest, hovered, and with its
// hint open (#409).
async function expectHintedAnswerAsWideAsPlainText(page) {
  await expect(token(page, 4)).toBeVisible();
  const atRest = await answerWidths(page);
  expect(Math.abs(atRest.hinted - atRest.plain)).toBeLessThanOrEqual(0.5);

  await token(page, 1).hover();
  const hovered = await answerWidths(page);
  expect(Math.abs(hovered.hinted - hovered.plain)).toBeLessThanOrEqual(0.5);

  await token(page, 4).click();
  await expect(token(page, 4)).toHaveAttribute('aria-expanded', 'true');
  const open = await answerWidths(page);
  expect(Math.abs(open.hinted - open.plain)).toBeLessThanOrEqual(0.5);
}

module.exports = { addWord, setUpLesson, token, expectHintedAnswerAsWideAsPlainText };
