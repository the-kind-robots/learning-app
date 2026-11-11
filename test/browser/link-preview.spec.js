const { createHash } = require('node:crypto');
const { test, expect } = require('./fixtures');
const { imageSize } = require('./image-size.shared');

// A messenger builds a link's preview from the page the server sends, running no script (#818): the spec reads
// that page's head, not the rendered one. `language` is the Accept-Language the messenger sends, when given.
async function previewTags(page, path, language) {
  const response = await page.request.get(path, language === undefined ? {} : { headers: { 'Accept-Language': language } });
  const html = await response.text();
  return page.evaluate((html) => {
    const head = new DOMParser().parseFromString(html, 'text/html').head;
    const tag = (selector) => head.querySelector(selector)?.getAttribute('content') ?? null;
    return {
      card: tag('meta[name="twitter:card"]'),
      descriptions: [
        tag('meta[property="og:description"]'),
        tag('meta[name="description"]'),
        tag('meta[name="twitter:description"]'),
      ],
      height: tag('meta[property="og:image:height"]'),
      image: tag('meta[property="og:image"]'),
      title: tag('meta[property="og:title"]'),
      url: tag('meta[property="og:url"]'),
      width: tag('meta[property="og:image:width"]'),
    };
  }, html);
}

// What a messenger fetches at the picture's address, read from this server: the suite's backend names the
// public address, where the picture is not yet. `version` is the start of the sha256 of the bytes served.
async function picture(page, address) {
  const { pathname } = new URL(address);
  const response = await page.request.get(pathname);
  const bytes = await response.body();
  return {
    size: await imageSize(page, pathname),
    status: response.status(),
    type: response.headers()['content-type'],
    version: createHash('sha256').update(bytes).digest('hex').slice(0, 8),
    weight: bytes.length,
  };
}

test('мессенджер разворачивает ссылку на приложение → название «Sprecha» и широкая картинка приложения, без описания', async ({ page }) => {
  let tags;
  let drawn;

  await test.step('Дано открытое приложение', async () => {
    await page.goto('/home');
  });

  await test.step('Когда мессенджер читает страницу по ссылке и берёт картинку', async () => {
    tags = await previewTags(page, '/home');
    drawn = await picture(page, tags.image);
  });

  await test.step('Тогда в ней название, адрес приложения, картинка по публичному адресу и ни строки описания', async () => {
    expect(tags).toMatchObject({ card: 'summary_large_image', descriptions: [null, null, null], height: '630', title: 'Sprecha', url: 'https://sprecha.de/', width: '1200' });
    expect(tags.image).toBe(`https://sprecha.de/link-preview/ru.png?v=${drawn.version}`);
  });

  await test.step('Тогда картинка — PNG того размера, что объявлен, легче 300 КБ, которые берёт WhatsApp', async () => {
    expect(drawn).toMatchObject({ size: '1200x630', status: 200, type: 'image/png' });
    expect(drawn.weight).toBeLessThan(300_000);
  });
});

test('мессенджер разворачивает ссылку на список слов или на урок → то же превью, что у главной', async ({ page }) => {
  let previews;

  await test.step('Дано открытое приложение', async () => {
    await page.goto('/home');
  });

  await test.step('Когда мессенджер читает главную, список слов и урок', async () => {
    previews = await Promise.all(['/home', '/words', '/lesson'].map((path) => previewTags(page, path)));
  });

  await test.step('Тогда превью одно — приложения', async () => {
    expect(previews[0].title).toBe('Sprecha');
    expect(previews[1]).toEqual(previews[0]);
    expect(previews[2]).toEqual(previews[0]);
  });
});

test('мессенджер называет язык приложения → картинка на этом языке, остальное превью то же', async ({ page }) => {
  const asked = [['ru-RU', 'ru'], ['en-US', 'en'], ['fr', 'fr'], ['tr-TR,tr;q=0.9', 'tr']];
  let previews;
  let drawn;

  await test.step('Дано открытое приложение', async () => {
    await page.goto('/home');
  });

  await test.step('Когда мессенджер читает страницу, называя русский, английский, французский и турецкий', async () => {
    previews = await Promise.all(asked.map(([language]) => previewTags(page, '/home', language)));
    drawn = await Promise.all(previews.map(({ image }) => picture(page, image)));
  });

  await test.step('Тогда у каждого языка своя картинка, а всё прочее — как у русского', async () => {
    for (const [index, [, language]] of asked.entries()) {
      expect(previews[index].image).toBe(`https://sprecha.de/link-preview/${language}.png?v=${drawn[index].version}`);
      expect({ ...previews[index], image: null }).toEqual({ ...previews[0], image: null });
      expect(drawn[index]).toMatchObject({ size: '1200x630', status: 200, type: 'image/png' });
      expect(drawn[index].weight).toBeLessThan(300_000);
    }
    expect(new Set(drawn.map(({ version }) => version)).size).toBe(asked.length);
  });
});

test('мессенджер не называет язык или первым называет тот, которого у приложения нет → картинка на русском', async ({ page }) => {
  let pictures;

  await test.step('Дано открытое приложение', async () => {
    await page.goto('/home');
  });

  await test.step('Когда мессенджер читает страницу без языка, с немецким, с «english» и с немецким перед французским', async () => {
    pictures = await Promise.all(['', 'de-DE', 'english', 'de-DE,fr;q=0.9'].map(async (language) => (await previewTags(page, '/home', language)).image));
  });

  await test.step('Тогда все картинки русские', async () => {
    for (const address of pictures) expect(new URL(address).pathname).toBe('/link-preview/ru.png');
  });
});
