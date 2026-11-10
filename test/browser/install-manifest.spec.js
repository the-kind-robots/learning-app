const { test, expect } = require('./fixtures');
const { addWord } = require('./add-form.shared');
const { imageSize } = require('./image-size.shared');
const { openControlled } = require('./service-worker.shared');

// The install offer, the installed icon and its shortcuts are drawn by the browser from the web manifest (#816),
// out of a page's reach, so the spec reads what the browser reads. Both manifests: the production one, and the
// development one the dev stand links. Each speaks the language the page speaks (#820).
const MANIFESTS = ['/manifest.json', '/dev-manifest.json'];
const LANGUAGES = ['ru', 'en', 'fr', 'tr'];

// The manifest at `path`, as the browser gets it; `language` stands for the system's language when given.
const readManifest = (page, path, language) => page.evaluate(async ([path, language]) => {
  const response = await fetch(path, language ? { headers: { 'Accept-Language': language } } : {});
  return response.json();
}, [path, language]);

// The language of the worker's copy of the manifest at `path`.
const cachedLanguage = (page, path) => page.evaluate(async (path) => {
  const copy = await caches.match(path);
  return copy && (await copy.json()).lang;
}, path);

// Cut by routing: setOffline leaves the worker's own fetch online (measured), routing reaches it.
const cutNetwork = (context) => context.route('**/*', (route) => route.abort('internetdisconnected'));

// The shortcut as the page's own manifest declares it: an installed app launches at its url.
async function shortcutUrl(page, name) {
  const manifest = await readManifest(page, await page.locator('link[rel="manifest"]').getAttribute('href'));
  return manifest.shortcuts.find((shortcut) => shortcut.name === name).url;
}

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

  test('браузер впервые открывает приложение, потом теряет сеть → манифест страницы есть', async ({ context, page }) => {
    let manifest;

    await test.step('Дано приложение, впервые открытое при сети, под управлением воркера', async () => {
      await openControlled(page);
    });

    await test.step('Когда сеть пропадает, страница перезагружается, и браузер читает её манифест', async () => {
      await cutNetwork(context);
      await page.reload();
      const devtools = await page.context().newCDPSession(page);
      manifest = await devtools.send('Page.getAppManifest');
    });

    await test.step('Тогда манифест разобран без ошибок, с русскими именем и подписью', async () => {
      expect(manifest.errors).toEqual([]);
      expect(JSON.parse(manifest.data)).toMatchObject({ lang: 'ru', short_name: 'Шпреха' });
    });
  });

  test('браузер читает манифест на турецком при сети, потом без сети → оба раза турецкий, а не копия с установки', async ({ context, page }) => {
    let online;
    let offline;

    await test.step('Дано приложение, открытое на русском, под управлением воркера', async () => {
      await openControlled(page);
    });

    await test.step('Когда браузер читает манифесты на турецком при сети, а потом без сети', async () => {
      online = await Promise.all(MANIFESTS.map((path) => readManifest(page, path, 'tr')));
      // The worker keeps an answer after it has handed it over.
      await expect.poll(() => Promise.all(MANIFESTS.map((path) => cachedLanguage(page, path)))).toEqual(['tr', 'tr']);
      await cutNetwork(context);
      offline = await Promise.all(MANIFESTS.map((path) => readManifest(page, path)));
    });

    await test.step('Тогда при сети каждый манифест турецкий, а не русская копия с установки', async () => {
      expect(online.map((manifest) => manifest.lang)).toEqual(['tr', 'tr']);
    });

    await test.step('Тогда без сети каждый — тот же турецкий', async () => {
      expect(offline.map((manifest) => manifest.lang)).toEqual(['tr', 'tr']);
    });
  });

  test('браузер загружает картинки манифеста → каждая того размера, что в нём объявлен', async ({ page }) => {
    let images;

    await test.step('Дано открытое приложение', async () => {
      await page.goto('/home');
    });

    await test.step('Когда браузер собирает картинки манифестов на каждом языке: иконки, снимки экрана, иконки ярлыков', async () => {
      const manifests = await Promise.all(MANIFESTS.flatMap((path) => LANGUAGES.map((language) => readManifest(page, path, language))));
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

test.describe('Установка приложения на турецком', () => {
  test.use({ locale: 'tr-TR' });

  test('браузер на турецком читает манифест → имя, подпись под иконкой, описание, ярлыки и снимки экрана по-турецки', async ({ page }) => {
    let manifests;

    await test.step('Дано приложение, открытое в браузере на турецком', async () => {
      await page.goto('/home');
    });

    await test.step('Когда браузер читает манифесты', async () => {
      manifests = await Promise.all(MANIFESTS.map((path) => readManifest(page, path)));
    });

    await test.step('Тогда в каждом турецкие имя, подпись, описание и ярлыки «Ders» и «Kelimeler»', async () => {
      for (const manifest of manifests) {
        expect(manifest).toMatchObject({ lang: 'tr', name: 'Sprecha: örneklerle Almanca kelimeler', short_name: 'Sprecha' });
        expect(manifest.description).toMatch(/öğren/);
        expect(manifest.shortcuts.map((shortcut) => shortcut.name)).toEqual(['Ders', 'Kelimeler']);
      }
    });

    await test.step('Тогда снимки экрана — турецкого интерфейса', async () => {
      for (const manifest of manifests) {
        for (const { src } of manifest.screenshots) expect(src).toMatch(/^\/screenshots\/tr\//);
      }
    });
  });
});
