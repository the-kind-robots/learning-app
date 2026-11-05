const { test, expect } = require('./fixtures');
const { answerWordThenShowExample, prompt } = require('./lesson-review.shared');

// On a phone the trials answered are swiped back to (#734).

// A scroll gesture left to right across the strip, through the browser's own scrolling and snapping. A touch
// gesture (`gestureSourceType: 'touch'`) does not reach headless Chrome under emulated touch: measured, the
// strip stays put, while this one moves it.
async function swipeRight(page) {
  // A view transition takes the input while it runs: the trial's own (`next-trial`) has to end first.
  await page.waitForFunction(() => document.getAnimations().length === 0);
  const box = await page.locator('page-strip').boundingBox();
  const client = await page.context().newCDPSession(page);
  await client.send('Input.synthesizeScrollGesture', {
    x: Math.round(box.x + box.width / 4),
    y: Math.round(box.y + box.height / 2),
    xDistance: Math.round(box.width / 2),
    yDistance: 0,
    gestureSourceType: 'default',
    speed: 1200,
  });
}

test('ученик на телефоне свайпает вправо → прошлое задание, его ответ полосой внизу, кнопка на месте', async ({ page }) => {
  let button;
  await test.step('Дано на экране пример после отвеченного слова', async () => {
    await answerWordThenShowExample(page, { missFirst: false });
    button = await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).boundingBox();
  });

  await test.step('Когда он свайпает вправо', async () => {
    await swipeRight(page);
  });

  await test.step('Тогда видно задание слова, его ответ в полосе внизу, кнопка «ДАЛЕЕ» там, где была «ПРОВЕРИТЬ»', async () => {
    await expect(prompt(page, 'пёс')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
    expect(await page.getByRole('button', { name: 'ДАЛЕЕ' }).boundingBox()).toEqual(button);
    const past = await page.locator('.lesson__footer--past').boundingBox();
    expect(past.y).toBeGreaterThan(page.viewportSize().height / 2);
  });
});
