const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// One Enter on the focused ДАЛЕЕ advances the lesson once (#277). The button
// used to carry a keydown handler that clicked it on top of the native Enter
// activation: two clicks, two advances. An advance now renders the next
// trial in the task of the click, so the second click of a double click
// finds ДАЛЕЕ gone and lands on the next trial, where a blank answer is not
// checked.

async function answerWrongWithThreeWords(page) {
  await page.goto('/home');
  await addWord(page, 'der Hund', 'пёс');
  await addWord(page, 'die Katze', 'кошка');
  await addWord(page, 'das Haus', 'дом');

  await page.goto('/lesson');
  await expect(page.locator('.lesson__prompt')).toBeVisible();
  await page.evaluate(() => {
    window.__continueClicks = 0;
    document.addEventListener('click', (event) => {
      if (event.target.id === 'lesson-next') window.__continueClicks += 1;
    }, true);
  });

  await page.locator('#lesson-answer').fill('falsch');
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  const next = page.getByRole('button', { name: 'ДАЛЕЕ' });
  await expect(next).toBeFocused();
  return next;
}

function collectLessonErrors(page) {
  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('use-cases.lesson')) {
      errors.push(message.text());
    }
  });
  return errors;
}

// Lets anything a second activation started land before the assertions.
async function settle(page) {
  await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 1000)));
}

test('Enter on ДАЛЕЕ advances once', async ({ page }) => {
  const lessonErrors = collectLessonErrors(page);
  await answerWrongWithThreeWords(page);
  await page.keyboard.press('Enter');

  await expect(page.locator('#lesson-answer')).toBeVisible();
  await settle(page);
  expect(await page.evaluate(() => window.__continueClicks)).toBe(1);
  expect(lessonErrors).toEqual([]);
});

test('a double click on ДАЛЕЕ advances once', async ({ page }) => {
  const lessonErrors = collectLessonErrors(page);
  const next = await answerWrongWithThreeWords(page);
  await next.dblclick();

  await expect(page.locator('#lesson-answer')).toBeVisible();
  await settle(page);
  expect(await page.evaluate(() => window.__continueClicks)).toBe(1);
  // Still waiting for an answer: the second click checked nothing.
  await expect(page.locator('#lesson-answer')).toBeVisible();
  expect(lessonErrors).toEqual([]);
});
