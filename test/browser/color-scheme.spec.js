const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedLessons } = require('./seed.shared');

// The app follows the phone's colour scheme (#413). At night the ground is night violet, the coloured
// buttons are deep under a light label, the answer's panel keeps its text readable, and today's stars burn
// as street lamps. Readability is the WCAG contrast of the colours as painted.

const ANSWERS = { дом: 'Haus', собака: 'Hund' };
const NIGHT_GROUND = 'rgb(21, 15, 28)';
const NIGHT_TEXT = 'rgb(243, 236, 248)';

function contrast([foreground, background]) {
  const luminance = (color) => {
    const [red, green, blue] = color.match(/[\d.]+/g).slice(0, 3).map((channel) => {
      const value = Number(channel) / 255;
      return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  };
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (lighter + 0.05) / (darker + 0.05);
}

// A big button's face is its ::before; its label is its own colour.
const labelOnFace = (button) => button.evaluate((element) =>
  [getComputedStyle(element).color, getComputedStyle(element, '::before').backgroundColor]);

// A text's colour and the nearest colour painted behind it: an ancestor's background or the ::before that
// draws it, as the lesson's panel does.
const textOnGround = (text) => text.evaluate((element) => {
  const painted = (color) => color !== 'rgba(0, 0, 0, 0)';
  for (let node = element; node; node = node.parentElement) {
    const before = getComputedStyle(node, '::before');
    if (before.content !== 'none' && painted(before.backgroundColor)) {
      return [getComputedStyle(element).color, before.backgroundColor];
    }
    if (painted(getComputedStyle(node).backgroundColor)) {
      return [getComputedStyle(element).color, getComputedStyle(node).backgroundColor];
    }
  }
  return null;
});

const ground = (page) => page.evaluate(() => [getComputedStyle(document.body).backgroundColor, getComputedStyle(document.body).color]);

async function answer(page, text) {
  await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(text);
  await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
}

test('ученик с тёмной темой на телефоне ошибается, потом отвечает верно → ночной фон, подписи синей, зелёной и красной кнопок и текст панелей читаются не хуже 4.5:1', async ({ page }) => {
  const next = page.getByRole('button', { name: 'ДАЛЕЕ' });

  await test.step('Дано тёмная тема телефона и два слова', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/home');
    await addWord(page, 'Haus', 'дом');
    await addWord(page, 'Hund', 'собака');
  });

  await test.step('Тогда главная на ночном фоне со светлым текстом, и «НАЧАТЬ УРОК» читается', async () => {
    expect(await ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    expect(contrast(await labelOnFace(page.getByRole('button', { name: 'НАЧАТЬ УРОК' })))).toBeGreaterThanOrEqual(4.5);
  });

  await test.step('Когда он начинает урок и вводит ответ', async () => {
    await page.getByRole('button', { name: 'НАЧАТЬ УРОК' }).click();
    await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill('Falsch');
  });

  await test.step('Тогда «ПРОВЕРИТЬ» читается', async () => {
    const check = page.getByRole('button', { name: 'ПРОВЕРИТЬ' });
    await expect(check).toBeEnabled();
    expect(contrast(await labelOnFace(check))).toBeGreaterThanOrEqual(4.5);
  });

  await test.step('Когда он отвечает неверно', async () => {
    await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
  });

  await test.step('Тогда «ДАЛЕЕ» тёмно-красная со светлой подписью, ответ светло-красный на тёмно-красной панели, и всё читается', async () => {
    const heading = page.getByRole('heading', { name: 'Правильно:' });
    await expect(heading).toBeVisible();
    await expect.poll(() => labelOnFace(next)).toEqual([NIGHT_TEXT, 'rgb(186, 28, 30)']);
    await expect.poll(() => textOnGround(heading)).toEqual(['rgb(247, 100, 100)', 'rgb(41, 10, 10)']);
    expect(contrast(await labelOnFace(next))).toBeGreaterThanOrEqual(4.5);
    expect(contrast(await textOnGround(heading))).toBeGreaterThanOrEqual(4.5);
  });

  await test.step('Когда он переходит дальше и отвечает верно', async () => {
    await next.click();
    await expect(page.locator('#lesson-answer')).toBeVisible();
    const prompt = (await page.locator('#lesson-challenge .lesson__prompt').textContent()).trim();
    await answer(page, ANSWERS[prompt]);
  });

  await test.step('Тогда зелёная «ДАЛЕЕ» и «Правильно!» на зелёной панели читаются', async () => {
    const heading = page.getByRole('heading', { name: 'Правильно!' });
    await expect(heading).toBeVisible();
    await expect.poll(async () => contrast(await labelOnFace(next))).toBeGreaterThanOrEqual(4.5);
    await expect.poll(async () => contrast(await textOnGround(heading))).toBeGreaterThanOrEqual(4.5);
  });
});

test('телефон переключается на тёмную тему при открытом приложении → экран темнеет без перезагрузки', async ({ page }) => {
  await test.step('Дано светлая тема и открытая главная', async () => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/home');
    await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    expect((await ground(page))[0]).toBe('rgb(255, 255, 255)');
    await page.evaluate(() => { window.__sameDocument = true; });
  });

  await test.step('Когда телефон переключается на тёмную тему', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
  });

  await test.step('Тогда главная на ночном фоне в том же документе', async () => {
    expect(await ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    expect(await page.evaluate(() => window.__sameDocument)).toBe(true);
  });
});

test('ученик с тёмной темой на телефоне прошёл сегодня шесть уроков → звезда за пять горит ртутью, звезда за урок — натрием, обе в ореоле из трёх слоёв', async ({ page }) => {
  const stars = page.getByRole('img', { name: /^Уроков за сегодня/ });

  await test.step('Дано тёмная тема телефона и шесть уроков за сегодня', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/home');
    await seedLessons(page, Array(6).fill(0));
    await page.reload();
    await expect(stars).toHaveAccessibleName('Уроков за сегодня: 6');
  });

  await test.step('Тогда звезда за пять уроков бело-зелёная, за урок — янтарная, и каждая светится в три слоя', async () => {
    const lamps = () => stars.evaluate((row) => [...row.querySelectorAll('svg')].map((svg) => ({
      body: getComputedStyle(svg.querySelector('.app-shell__star-body')).fill,
      glows: getComputedStyle(svg).filter.split('drop-shadow').length - 1,
    })).sort((a, b) => a.body.localeCompare(b.body)));
    await expect.poll(lamps).toEqual([
      { body: 'rgb(205, 255, 238)', glows: 3 },
      { body: 'rgb(255, 168, 30)', glows: 3 },
    ]);
  });
});
