const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { seedLessons } = require('./seed.shared');

// The app follows the phone's colour scheme (#413). At night the ground is night violet, the coloured
// buttons are deep under a light label, the answer's panel keeps its text readable, and today's stars burn
// as street lamps. Readability is the WCAG contrast of the colours as painted.

// No service worker: once installed it serves the app's script past the route that keeps the splash on screen
// for a reopened app. An online reopen fetches the page from the server either way.
test.use({ serviceWorkers: 'block' });

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

// The learner picks the scheme in the settings under the word mark (#805). The pick is the device's: a cookie
// the server renders the splash and the browser's bar from, so a reopened app is in it from the first paint.
const settings = (page) => page.getByRole('dialog', { name: 'Настройки' });
const scheme = (page, name) => settings(page).getByRole('group', { name: 'Оформление' }).getByRole('radio', { name });

async function pickScheme(page, name) {
  await page.getByRole('button', { name: /^Шпреха/ }).click();
  await scheme(page, name).click();
}

// The colour the browser tints its bar with: the first theme-color whose media matches.
const barColour = (page) => page.evaluate(() => [...document.querySelectorAll('meta[name="theme-color"]')]
  .find((meta) => !meta.media || matchMedia(meta.media).matches)?.content);

const splashGround = async (page) => {
  await page.route('**/js/app/main.js', (route) => route.abort());
  await page.reload();
  const colour = await page.locator('.splash').evaluate((splash) => getComputedStyle(splash).backgroundColor);
  await page.unroute('**/js/app/main.js');
  return colour;
};

// What moves over the next frames, at most at once: the CSS transitions — a control that eases into the new
// colours on its own, still catching up after the rest — and the animations of the whole screen's dissolve.
const switchMotion = (page) => page.evaluate(() => new Promise((resolve) => {
  const most = { catchingUp: 0, dissolving: 0 };
  let frames = 0;
  const look = () => {
    const animations = document.getAnimations();
    most.catchingUp = Math.max(most.catchingUp, animations.filter((animation) => animation instanceof CSSTransition).length);
    most.dissolving = Math.max(most.dissolving,
      animations.filter((animation) => (animation.effect.pseudoElement || '').startsWith('::view-transition')).length);
    frames += 1;
    if (frames < 20) requestAnimationFrame(look);
    else resolve(most);
  };
  look();
}));

test('ученик со светлой темой на телефоне выбирает в настройках «Тёмная» → приложение и полоса браузера темнеют сразу, все цвета разом, а открытое заново оно тёмное с заставки', async ({ page }) => {
  await test.step('Дано светлая тема телефона и открытая главная', async () => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/home');
    await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    await page.evaluate(() => { window.__sameDocument = true; });
  });

  await test.step('Тогда в настройках отмечена «Система»', async () => {
    await page.getByRole('button', { name: /^Шпреха/ }).click();
    await expect(scheme(page, 'Система')).toBeChecked();
    await page.keyboard.press('Escape');
  });

  await test.step('Когда он выбирает «Тёмная»', async () => {
    await pickScheme(page, 'Тёмная');
  });

  await test.step('Тогда ни один цвет не догоняет остальные', async () => {
    expect((await switchMotion(page)).catchingUp).toBe(0);
  });

  await test.step('Тогда главная на ночном фоне в том же документе, полоса браузера ночная, «Тёмная» отмечена', async () => {
    await expect.poll(() => ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    expect(await page.evaluate(() => window.__sameDocument)).toBe(true);
    await expect.poll(() => barColour(page)).toBe('#150f1c');
    await expect(scheme(page, 'Тёмная')).toBeChecked();
  });

  await test.step('Когда он открывает приложение заново', async () => {
    expect(await splashGround(page)).toBe(NIGHT_GROUND);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
  });

  await test.step('Тогда и заставка, и главная на ночном фоне', async () => {
    expect(await ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    expect(await barColour(page)).toBe('#150f1c');
  });
});

test('ученик с тёмной темой на телефоне выбирает «Светлая» → приложение светлое и после перезапуска', async ({ page }) => {
  await test.step('Дано тёмная тема телефона', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/home');
    expect(await ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
  });

  await test.step('Когда он выбирает «Светлая»', async () => {
    await pickScheme(page, 'Светлая');
  });

  await test.step('Тогда главная белая, и полоса браузера белая', async () => {
    await expect.poll(async () => (await ground(page))[0]).toBe('rgb(255, 255, 255)');
    await expect.poll(() => barColour(page)).toBe('#ffffff');
  });

  await test.step('Тогда открытое заново приложение белое с заставки', async () => {
    expect(await splashGround(page)).toBe('rgb(255, 255, 255)');
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Главная' })).toBeAttached();
    expect((await ground(page))[0]).toBe('rgb(255, 255, 255)');
  });
});

test('ученик с выбранной тёмной темой выбирает «Система» → приложение снова следует телефону', async ({ page }) => {
  await test.step('Дано светлая тема телефона и выбранная «Тёмная»', async () => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/home');
    await pickScheme(page, 'Тёмная');
    await expect.poll(() => ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    await page.keyboard.press('Escape');
  });

  await test.step('Когда он выбирает «Система»', async () => {
    await pickScheme(page, 'Система');
  });

  await test.step('Тогда главная белая', async () => {
    await expect.poll(async () => (await ground(page))[0]).toBe('rgb(255, 255, 255)');
  });

  await test.step('Когда телефон переключается на тёмную тему', async () => {
    await page.emulateMedia({ colorScheme: 'dark' });
  });

  await test.step('Тогда главная на ночном фоне', async () => {
    expect(await ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
  });
});

test.describe('на телефоне', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true });

  test('ученик выбирает «Тёмная» → весь экран вместе с меню растворяется в ночи, ни один цвет не догоняет остальные', async ({ page }) => {
    await test.step('Дано светлая тема телефона и открытые настройки', async () => {
      await page.emulateMedia({ colorScheme: 'light' });
      await page.goto('/home');
      await page.getByRole('button', { name: /^Шпреха/ }).click();
      await expect(scheme(page, 'Система')).toBeChecked();
    });

    await test.step('Когда он выбирает «Тёмная»', async () => {
      await scheme(page, 'Тёмная').click();
    });

    await test.step('Тогда экран растворяется целиком, и ни один цвет не догоняет остальные', async () => {
      const motion = await switchMotion(page);
      expect(motion.dissolving).toBeGreaterThan(0);
      expect(motion.catchingUp).toBe(0);
    });

    await test.step('Тогда экран на ночном фоне', async () => {
      await expect.poll(() => ground(page)).toEqual([NIGHT_GROUND, NIGHT_TEXT]);
    });
  });
});

test.describe('на немецкой системе', () => {
  test.use({ locale: 'de-DE' });

  test('пользователь без выбора открывает приложение → настройки спрашивают только язык, оформления в них нет', async ({ page }) => {
    await test.step('Когда он открывает приложение', async () => {
      await page.goto('/');
    });

    await test.step('Тогда открыты настройки с языками и без оформления', async () => {
      const asking = page.getByRole('dialog', { name: 'Settings' });
      await expect(asking.getByRole('radio', { name: 'English' })).toBeChecked();
      await expect(asking.getByRole('group', { name: 'Appearance' })).toHaveCount(0);
    });
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
