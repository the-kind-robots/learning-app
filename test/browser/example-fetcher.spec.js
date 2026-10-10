const { test, expect } = require('./fixtures');
const { becomeAccount } = require('./account.shared');

// The example fetcher (#523, ADR-0021): a visible tab of a device with an
// account asks, and every failure has one effect that ends on its own. The
// endpoint is stubbed with `page.route`; what is asserted is when, and how
// often, the app asks.
//
// Playwright cannot hide a page, so `document.hidden` is shimmed and the real
// `visibilitychange` dispatched, as in `dictionary-focus.spec.js`.

const PAGE_STATE_SHIM = () => {
  let isHidden = false;
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => isHidden });
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => (isHidden ? 'hidden' : 'visible'),
  });
  window.__setHidden = (value) => {
    isHidden = value;
    document.dispatchEvent(new Event('visibilitychange'));
  };
};

const hide = (page) => page.evaluate(() => window.__setHidden(true));
const show = (page) => page.evaluate(() => window.__setHidden(false));

// A plain wait, named so the reason is visible at the call site: these
// assertions are that nothing is asked, and nothing being asked takes time
// to establish.
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

// Records the words the page asks the endpoint about, with when it asked,
// and answers each request with `respond`.
async function recordRequests(page, respond) {
  const requested = [];
  await page.route('**/api/examples*', async (route) => {
    requested.push({ at: Date.now(), word: new URL(route.request().url()).searchParams.get('word') });
    await respond(route);
  });
  return requested;
}

// Never answers: the request stays in flight until the page aborts it.
const hang = () => new Promise(() => {});

const status = (code, headers = {}) => (route) =>
  route.fulfill({ status: code, headers, contentType: 'application/json', body: '{"error":"stub"}' });

async function memoryReady(page) {
  await page.waitForFunction(
    () => typeof window.__metrics === 'function' && (window.__metrics().memory || {})['ready-ms'],
  );
}

async function open(page, respond, { account = true } = {}) {
  await page.addInitScript(PAGE_STATE_SHIM);
  const requested = await recordRequests(page, respond);
  await page.goto('/home');
  await memoryReady(page);
  if (account) {
    await becomeAccount(page);
    await memoryReady(page);
  }
  return requested;
}

// Words without examples, written past the app as a replication writes them;
// memory takes them from the change feed and the fetcher looks at them.
async function seedWords(page, values) {
  await page.evaluate(async (values) => {
    const now = new Date().toISOString();
    await db.use('user-db').bulkDocs(values.map((value) => ({
      _id: 'vocab:' + value.toLowerCase(), type: 'vocab', value,
      translation: [{ lang: 'ru', value: value + '-ru' }], created_at: now, modified_at: now,
    })));
  }, values);
}

const requestsMade = (requested) => () => requested.length;

test.describe('Загрузка примеров', () => {
  test('пользователь скрывает вкладку и показывает снова → скрытая вкладка ничего не просит, показанная просит то же слово', async ({ page }) => {
    let requested;
    await test.step('Дано устройство с аккаунтом и скрытая вкладка', async () => {
      requested = await open(page, hang);
      await hide(page);
    });

    await test.step('Когда в память приходит слово без примера', async () => {
      await seedWords(page, ['Hund']);
      await nothingHappensFor(page, 4000);
    });

    await test.step('Тогда скрытая вкладка ничего не просит', async () => {
      expect(requested).toHaveLength(0);
    });

    await test.step('Когда вкладку показывают', async () => {
      await show(page);
    });

    await test.step('Тогда она просит пример', async () => {
      await expect.poll(requestsMade(requested), { timeout: 15000 }).toBe(1);
    });

    // Hidden, the tab aborts its request; nothing is marked, so shown again it
    // asks for the same pair.
    await test.step('Когда вкладку скрывают посреди запроса и показывают снова', async () => {
      await hide(page);
      await nothingHappensFor(page, 3000);
      expect(requested).toHaveLength(1);
      await show(page);
    });

    await test.step('Тогда то же слово спрашивается ещё раз', async () => {
      await expect.poll(() => requested.map((r) => r.word), { timeout: 15000 }).toEqual(['Hund', 'Hund']);
    });
  });

  test('пользователь без аккаунта получает слово без примера → ничего не запрашивается', async ({ page }) => {
    let requested;
    await test.step('Дано устройство без аккаунта', async () => {
      requested = await open(page, status(200), { account: false });
    });

    await test.step('Когда в память приходит слово без примера', async () => {
      await seedWords(page, ['Igel']);
      await nothingHappensFor(page, 5000);
    });

    await test.step('Тогда пример не запрашивается', async () => {
      expect(requested).toHaveLength(0);
    });
  });

  test('сервер отвечает 429 с Retry-After → до конца паузы ничего не просится, потом то же слово', async ({ page }) => {
    let requested;
    await test.step('Дано устройство с аккаунтом и сервер, ограничивающий запросы', async () => {
      requested = await open(page, status(429, { 'Retry-After': '5' }));
    });

    await test.step('Когда приходит слово без примера и сервер отвечает 429', async () => {
      await seedWords(page, ['Katze']);
      await expect.poll(requestsMade(requested), { timeout: 15000 }).toBe(1);
    });

    await test.step('Тогда внутри Retry-After ничего не просится', async () => {
      await nothingHappensFor(page, 4000);
      expect(requested).toHaveLength(1);
    });

    await test.step('Тогда после паузы то же слово просится снова', async () => {
      await expect.poll(requestsMade(requested), { timeout: 15000 }).toBe(2);
      expect(requested[1].word).toBe('Katze');
      expect(requested[1].at - requested[0].at).toBeGreaterThanOrEqual(5000);
    });
  });

  test('сервер отвечает 503 → пауза для всех слов, потом то же слово', async ({ page }) => {
    let requested;
    await test.step('Дано устройство с аккаунтом и недоступный генератор', async () => {
      requested = await open(page, status(503, { 'Retry-After': '5' }));
    });

    await test.step('Когда приходят два слова без примера и сервер отвечает 503', async () => {
      await seedWords(page, ['Maus', 'Igel']);
      await expect.poll(requestsMade(requested), { timeout: 15000 }).toBe(1);
    });

    // The server's problem, not the pair's: nothing for 5 s, then the same pair.
    await test.step('Тогда пять секунд ничего не просится', async () => {
      await nothingHappensFor(page, 4000);
      expect(requested).toHaveLength(1);
    });

    await test.step('Тогда после паузы просится то же слово', async () => {
      await expect.poll(requestsMade(requested), { timeout: 15000 }).toBe(2);
      expect(requested[1].word).toBe(requested[0].word);
      expect(requested[1].at - requested[0].at).toBeGreaterThanOrEqual(5000);
    });
  });
});
