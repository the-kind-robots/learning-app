const { test, expect } = require('./fixtures');
const { openHome } = require('./service-worker.shared');

// GH-535: the suggestion list is kept across keystrokes so it does not flash
// empty (#178). Typed past, it stayed on screen and tappable until the next
// answer; a touch on the kept row put «der Rücken» under «Rückenkurse». Now a
// keystroke takes out the rows it left behind in the keystroke itself.
//
// `der Rücken` is the only fixture lemma under `rück` (README.md), so one
// keystroke past it leaves nothing; its umlaut and article are the two things
// the comparison has to see through.

const options = (page) => page.getByRole('option');

// The value field's label follows the mode, so it is addressed by id.
const valueField = (page) => page.locator('#new-word-value');

const translationField = (page) => page.getByLabel('Перевод (русский)');

// One keystroke, read back in the task that handled it. The debounce that
// would ask the dictionary is a timer, so nothing it could answer with has
// had a chance to run: what is counted is the kept list, narrowed or not.
const rowsRightAfterTyping = (field, text) => field.evaluate((node, typed) => {
  node.value += typed;
  node.dispatchEvent(new Event('input', { bubbles: true }));
  return Array.from(document.querySelectorAll('[role="option"]'), (row) => row.innerText.trim());
}, text);

test.describe('Подсказки слов при вводе', () => {
  test('пользователь печатает дальше слова из подсказки → устаревшая подсказка исчезает сразу, и нажать её нельзя', async ({ page }) => {
    const field = valueField(page);
    let lastRow;
    let rowsAfterKeystroke;

    await test.step('Дано подсказка «der Rücken» на «Rücken» и перевод «спина» в поле', async () => {
      await openHome(page);
      await field.pressSequentially('Rücken');
      await expect(options(page)).toHaveText(['der Rücken']);
      await expect(translationField(page)).toHaveValue('спина');
      lastRow = await options(page).last().boundingBox();
    });

    await test.step('Когда он печатает «k»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 'k');
    });

    await test.step('Тогда подсказок нет уже в задаче самого нажатия, до ответа словаря', async () => {
      expect(rowsAfterKeystroke).toEqual([]);
    });

    await test.step('Когда он допечатывает «urse» по 60 мс и нажимает туда, где была подсказка', async () => {
      // The cadence of the report; the answer for every prefix here is empty,
      // so only the kept list could have put a row back.
      await field.pressSequentially('urse', { delay: 60 });
      await page.mouse.click(lastRow.x + lastRow.width / 2, lastRow.y + lastRow.height / 2);
    });

    await test.step('Тогда в поле слова «Rückenkurse», а перевод не «спина»', async () => {
      await expect(field).toHaveValue('Rückenkurse');
      await expect(translationField(page)).not.toHaveValue('спина');
    });
  });

  test('пользователь печатает словоформу, а не лемму → подсказка леммы остаётся, без мигания', async ({ page }) => {
    const field = valueField(page);
    let rowsAfterKeystroke;

    await test.step('Дано подсказка «das Haus» на «Häu» — по форме «Häuser»', async () => {
      await openHome(page);
      await field.pressSequentially('Häu');
      await expect(options(page)).toHaveText(['das Haus']);
    });

    await test.step('Когда он печатает «s»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 's');
    });

    await test.step('Тогда «das Haus» остаётся и в задаче нажатия, и после ответа словаря', async () => {
      expect(rowsAfterKeystroke).toEqual(['das Haus']);
      await expect(options(page)).toHaveText(['das Haus']);
    });
  });

  test('пользователь печатает букву, с которой часть подсказок продолжается → они остаются, без мигания', async ({ page }) => {
    const field = valueField(page);
    let rowsAfterKeystroke;

    await test.step('Дано четыре подсказки на «Fe»', async () => {
      await openHome(page);
      await field.pressSequentially('Fe');
      await expect(options(page)).toHaveCount(4);
    });

    await test.step('Когда он печатает «n»', async () => {
      rowsAfterKeystroke = await rowsRightAfterTyping(field, 'n');
    });

    await test.step('Тогда в задаче нажатия остались две подсказки на «Fen», и ответ словаря их не меняет', async () => {
      expect(rowsAfterKeystroke).toEqual(['das Fenster', 'die Fensterbank']);
      await expect(options(page)).toHaveText(['das Fenster', 'die Fensterbank']);
    });
  });
});
