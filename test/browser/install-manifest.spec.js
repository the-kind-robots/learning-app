const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');

// The install offer, the installed icon and its shortcuts are drawn by the browser from the web manifest (#816),
// out of a page's reach, so the spec reads what the browser reads. Both manifests: the production one, and the
// development one the dev stand links.
const MANIFESTS = ['/manifest.json', '/dev-manifest.json'];

const readManifest = (page, path) => page.evaluate(async (path) => (await fetch(path)).json(), path);

// The shortcut as the page's own manifest declares it: an installed app launches at its url.
async function shortcutUrl(page, name) {
  const manifest = await readManifest(page, await page.locator('link[rel="manifest"]').getAttribute('href'));
  return manifest.shortcuts.find((shortcut) => shortcut.name === name).url;
}

// The size an image really has, loaded the way the browser loads a manifest image.
const imageSize = (page, src) => page.evaluate((src) => new Promise((resolve, reject) => {
  const image = new Image();
  image.onload = () => resolve(`${image.naturalWidth}x${image.naturalHeight}`);
  image.onerror = () => reject(new Error(`${src} did not load`));
  image.src = src;
}), src);

test.describe('Установка приложения', () => {
  test('ученик выбирает ярлык «Урок» на иконке → открывается урок', async ({ page }) => {
    let url;

    await test.step('Дано слово в словаре и ярлык «Урок» из манифеста', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      url = await shortcutUrl(page, 'Урок');
    });

    await test.step('Когда приложение открывают ярлыком', async () => {
      await page.goto(url);
    });

    await test.step('Тогда на экране урок', async () => {
      await expect(page.getByRole('progressbar', { name: 'Прогресс урока' })).toBeVisible();
    });
  });

  test('ученик выбирает ярлык «Слова» на иконке → открывается список слов', async ({ page }) => {
    let url;

    await test.step('Дано слово в словаре и ярлык «Слова» из манифеста', async () => {
      await page.goto('/home');
      await addWord(page, 'Haus', 'дом');
      url = await shortcutUrl(page, 'Слова');
    });

    await test.step('Когда приложение открывают ярлыком', async () => {
      await page.goto(url);
    });

    await test.step('Тогда на экране список слов', async () => {
      await expect(page.getByRole('heading', { name: 'Мои слова' })).toBeVisible();
    });
  });

  test('браузер читает манифест → русское имя и описание, адаптивная иконка, пять снимков экрана для телефона и ни одного широкого', async ({ page }) => {
    let manifests;

    await test.step('Дано открытое приложение', async () => {
      await page.goto('/home');
    });

    await test.step('Когда браузер читает манифесты', async () => {
      manifests = await Promise.all(MANIFESTS.map((path) => readManifest(page, path)));
    });

    await test.step('Тогда в каждом русские имя, подпись под иконкой и описание', async () => {
      for (const manifest of manifests) {
        expect(manifest).toMatchObject({ lang: 'ru', short_name: 'Шпреха' });
        expect(manifest.name).toMatch(/^Шпреха/);
        expect(manifest.description).toMatch(/[а-яё]/);
      }
    });

    await test.step('Тогда в каждом адаптивная иконка на 192 и 512 точек', async () => {
      for (const manifest of manifests) {
        const maskable = manifest.icons.filter((icon) => icon.purpose === 'maskable').map((icon) => icon.sizes);
        expect(maskable.sort()).toEqual(['192x192', '512x512']);
      }
    });

    await test.step('Тогда в каждом пять снимков экрана, все для телефона', async () => {
      for (const manifest of manifests) {
        expect(manifest.screenshots.map((screenshot) => screenshot.form_factor)).toEqual(Array(5).fill('narrow'));
      }
    });

    await test.step('Тогда Chrome разбирает манифест страницы без ошибок', async () => {
      const devtools = await page.context().newCDPSession(page);
      const { errors } = await devtools.send('Page.getAppManifest');
      expect(errors).toEqual([]);
    });
  });

  test('браузер читает манифест → приложение узнаётся по тому же id, что и до его объявления', async ({ page }) => {
    let manifests;

    await test.step('Дано открытое приложение', async () => {
      await page.goto('/home');
    });

    await test.step('Когда браузер читает манифесты', async () => {
      manifests = await Promise.all(MANIFESTS.map((path) => readManifest(page, path)));
    });

    await test.step('Тогда id каждого — прежний адрес запуска /home', async () => {
      for (const manifest of manifests) {
        expect(manifest).toMatchObject({ id: '/home', start_url: '/home' });
      }
    });
  });

  test('браузер загружает картинки манифеста → каждая того размера, что в нём объявлен', async ({ page }) => {
    let images;

    await test.step('Дано открытое приложение', async () => {
      await page.goto('/home');
    });

    await test.step('Когда браузер собирает картинки манифестов: иконки, снимки экрана, иконки ярлыков', async () => {
      const manifests = await Promise.all(MANIFESTS.map((path) => readManifest(page, path)));
      images = manifests.flatMap((manifest) => [
        ...manifest.icons,
        ...manifest.screenshots,
        ...manifest.shortcuts.flatMap((shortcut) => shortcut.icons),
      ]);
    });

    await test.step('Тогда каждая загружается того размера, что объявлен: иначе Chrome её молча отбросит', async () => {
      for (const { src, sizes } of images) {
        expect(`${src} ${await imageSize(page, src)}`).toBe(`${src} ${sizes}`);
      }
    });
  });
});
