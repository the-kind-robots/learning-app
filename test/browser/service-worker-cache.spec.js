const { test, expect } = require('./fixtures');
const { openControlled } = require('./service-worker.shared');

// What the worker's cache keeps and what it hands back (#315, #299). Every
// test here starts with the page under a controller.

const ASSET = '/css/styles.css';
const MANIFEST = '/dictionary/manifest';

// A fetch from the page, so it passes through the worker.
const statusOf = (page, path) => page.evaluate(async (p) => (await fetch(p)).status, path);

// What the bucket holds for a path, read straight from the cache.
const cachedEntry = (page, path) => page.evaluate(async (p) => {
  const response = await caches.match(p);
  return response && { status: response.status, body: await response.text() };
}, path);

test.describe('Кэш приложения без сети', () => {
  test('ресурс один раз не загрузился → при следующем запросе берётся заново, а не из кэша', async ({ context, page }) => {
    await test.step('Дано страница под управлением воркера, ресурса нет в кэше', async () => {
      await openControlled(page);
      // Precached at install, so it has to leave the bucket before the worker
      // will go to the network for it.
      await page.evaluate(async (path) => {
        const bucket = (await caches.keys()).find((key) => key.startsWith('shell-'));
        await (await caches.open(bucket)).delete(path);
      }, ASSET);
      // One failure, then the real file.
      await context.route(`**${ASSET}`, (route) => route.fulfill({ status: 500, body: 'boom' }), { times: 1 });
    });

    await test.step('Когда страница запрашивает ресурс, и сервер один раз отвечает ошибкой', async () => {
      expect(await statusOf(page, ASSET)).toBe(500);
    });

    await test.step('Тогда повторный запрос получает настоящий файл', async () => {
      // Had the 500 been kept, it would answer this one too.
      expect(await statusOf(page, ASSET)).toBe(200);
      await expect.poll(async () => (await cachedEntry(page, ASSET))?.status).toBe(200);
    });
  });

  test('список словарей не загрузился → в кэше остаётся прежний список', async ({ context, page }) => {
    let before;

    await test.step('Дано страница под управлением воркера, список словарей в кэше', async () => {
      await openControlled(page);
      expect(await statusOf(page, MANIFEST)).toBe(200);
      await expect.poll(async () => (await cachedEntry(page, MANIFEST))?.body).toContain('hash');
      before = await cachedEntry(page, MANIFEST);
    });

    await test.step('Когда сервер отвечает на запрос списка ошибкой', async () => {
      await context.route(`**${MANIFEST}`, (route) => route.fulfill({ status: 500, body: 'boom' }));
      expect(await statusOf(page, MANIFEST)).toBe(500);
    });

    await test.step('Тогда в кэше прежний список', async () => {
      expect(await cachedEntry(page, MANIFEST)).toEqual(before);
    });
  });
});
