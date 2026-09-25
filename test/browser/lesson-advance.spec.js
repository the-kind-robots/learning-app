const { test, expect } = require('@playwright/test');
const { addWord } = require('./lesson-answer.shared');

// One Enter on the focused ДАЛЕЕ advances the lesson once (#277). The button
// used to carry a keydown handler that clicked it on top of the native Enter
// activation: two clicks, two advances reading the same revision, and the
// second save failing with «Document update conflict». A double click is two
// real activations, so there the lesson itself advances once for both.

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

// Both advances were in flight together, so the second one failed its save
// after the first had rendered the next trial. Letting the store settle is
// what lets a late failure show up; a positive assertion alone would pass
// before it arrives.
async function letTheSavesSettle(page) {
  await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 1000)));
}

test('Enter on ДАЛЕЕ advances once, without a failed save', async ({ page }) => {
  const lessonErrors = collectLessonErrors(page);
  await answerWrongWithThreeWords(page);
  await page.keyboard.press('Enter');

  await expect(page.locator('#lesson-answer')).toBeVisible();
  await letTheSavesSettle(page);
  expect(await page.evaluate(() => window.__continueClicks)).toBe(1);
  expect(lessonErrors).toEqual([]);
});

test('a double click on ДАЛЕЕ advances once, without a failed save', async ({ page }) => {
  const lessonErrors = collectLessonErrors(page);
  const next = await answerWrongWithThreeWords(page);
  await next.dblclick();

  await expect(page.locator('#lesson-answer')).toBeVisible();
  await letTheSavesSettle(page);
  expect(await page.evaluate(() => window.__continueClicks)).toBe(2);
  expect(lessonErrors).toEqual([]);
});
