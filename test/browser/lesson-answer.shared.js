const { expect, docsOfType } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// Lesson setup shared by the desktop and the phone answer specs.
//
// The examples backend needs an external API, so the example document is
// seeded straight into the app's user-db through the dev-build globals —
// the same layer the app itself uses (see test/browser/README.md).

// Seeds at the engine level (`db`, the PouchDB wrapper) a document into the
// database that holds examples. The document itself is built by the app's
// pure `example-doc`, so it has the id and body the app writes; the app's
// stateful layers need the `dbs` map that only `init!` in `main` builds, so a
// spec does not reach for them — see test/browser/README.md.
async function seedExample(page) {
  const [{ _id: wordId }] = await docsOfType(page, 'user-db', 'vocab');
  await page.evaluate(async (wordId) => {
    const kw = cljs.core.keyword;
    const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
    await db.insert(db.use('user-db'), adapters.learner.documents.example_doc(wordId, 'der Hund', null, toClj({
      'value': 'Der Hund schläft im Garten.',
      'translation': 'Пёс спит в саду.',
      'structure': [
        { usedForm: 'Hund', dictionaryForm: 'der Hund', translation: 'пёс', wordIndex: 1 },
        { usedForm: 'Garten', dictionaryForm: 'der Garten', translation: 'сад', wordIndex: 4 },
      ],
    })));
  }, wordId);
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

module.exports = { setUpLesson, token, answerWidths };
