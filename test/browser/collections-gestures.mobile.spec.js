const fs = require('fs');
const os = require('os');
const path = require('path');
const { test, expect, chromium, nothingHappensFor } = require('./fixtures');

// The issue's phone (#456): 384 × 800.
test.use({ viewport: { width: 384, height: 800 } });

// Enough tiles that the screen scrolls, and the names from the report.
const collections = [
  ['arbeit-meet', 'Arbeit/Meetings und Besprechungen mit Kollegen', ['vocab:a', 'vocab:b', 'vocab:c']],
  ['arbeit-mail', 'Arbeit/E-Mails', ['vocab:i']],
  ['reise', 'Reise', ['vocab:a']],
  ['reise-unt', 'Reise/Unterkunftsmöglichkeiten', ['vocab:d', 'vocab:e']],
  ['dt-muend', 'Deutsch-Test/Mündliche Prüfung Teil 1', ['vocab:f']],
  ['lang', 'Zusammenarbeitsvereinbarungen', ['vocab:g', 'vocab:h']],
  ['xs', 'x'.repeat(40), []],
  ...['Alltag', 'Bahn', 'Essen', 'Familie', 'Farben', 'Garten', 'Haus', 'Kino', 'Kleidung', 'Körper',
      'Natur', 'Schule', 'Sport', 'Stadt', 'Tiere', 'Wetter', 'Zahlen', 'Zeit']
    .map((name) => [name.toLowerCase(), name, []]),
];

async function seedCollections(page) {
  await page.evaluate(async (collections) => {
    const now = new Date().toISOString();
    const docs = collections.map(([id, name, wordIds]) => ({
      _id: 'collection:' + id, type: 'collection', name, word_ids: wordIds, created_at: now,
    }));
    await db.bulk_docs(db.use('user-db'), docs);
  }, collections);
}

async function openCollections(page, base = '') {
  await page.goto(base + '/');
  await seedCollections(page);
  await page.getByRole('button', { name: 'Открыть наборы' }).click();
  await expect(page.getByRole('button', { name: 'Alltag 0', exact: true })).toBeVisible();
}

// Touch through the protocol, so Chrome runs its own gesture recognition and
// issues a real `pointercancel` when it hands the touch to the scroller.
async function touch(page) {
  const cdp = await page.context().newCDPSession(page);
  const send = (type, points) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: points });
  return {
    down: (x, y) => send('touchStart', [{ x, y }]),
    move: (x, y) => send('touchMove', [{ x, y }]),
    up: () => send('touchEnd', []),
  };
}

async function centre(locator) {
  await locator.scrollIntoViewIfNeeded();
  const box = await locator.boundingBox();
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 };
}

// Counts what the pointer stream did, from before any app listener sees it.
// `suppress` names event types stopped at the window before the page sees
// them, to shape the stream into the one a phone sends.
async function recordPointer(page, suppress = []) {
  await page.evaluate((suppress) => {
    window.__pointer = { cancels: 0 };
    window.addEventListener('pointercancel', () => { window.__pointer.cancels += 1; }, { capture: true });
    for (const type of suppress) {
      window.addEventListener(type, (e) => {
        if (e.pointerType !== 'mouse') {
          e.stopPropagation();
          e.preventDefault();
        }
      }, { capture: true });
    }
  }, suppress);
}

// Held until the ✕ shows, which is what the long press is for.
async function longPress(page, locator, close) {
  const finger = await touch(page);
  const { x, y } = await centre(locator);
  await finger.down(x, y);
  await expect(close).toHaveCSS('opacity', '1');
  await finger.up();
}

// The count's digits, not its box: in a row the box is stretched to the
// row's height, its text on the first line.
const textRect = (el) => {
  const range = document.createRange();
  range.selectNodeContents(el);
  const r = range.getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height };
};

// Layout box within the tile, untouched by the editing zoom (`scale`).
const layoutBox = (el) => {
  const tile = el.closest('.tile');
  const a = el.getBoundingClientRect();
  const t = tile.getBoundingClientRect();
  const k = tile.offsetWidth / t.width;
  const px = (v) => Math.round(v * 2) / 2;
  return { left: px((a.left - t.left) * k), top: px((a.top - t.top) * k), width: el.offsetWidth, height: el.offsetHeight };
};

const overlaps = (a, b) =>
  a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;

// Where each line of a text node starts, as character offsets.
const lineStarts = (el) => {
  const text = el.firstChild;
  const range = document.createRange();
  const starts = [];
  let top = null;
  for (let i = 0; i < text.length; i += 1) {
    range.setStart(text, i);
    range.setEnd(text, i + 1);
    // A character at a line start can also report an empty box at the end
    // of the line before; the last box is where it is drawn.
    const boxes = range.getClientRects();
    const rect = boxes[boxes.length - 1];
    if (rect && rect.top !== top) {
      if (top !== null) starts.push(i);
      top = rect.top;
    }
  }
  return starts;
};

// Chrome on Linux hyphenates only with the hyphenation data its component
// updater downloads into a profile; a test profile starts without it and
// Playwright turns the updater off. The local Chrome profile's copy is
// borrowed when there is one; without it (CI) there is nothing to measure.
const hyphenData = path.join(os.homedir(), '.config/google-chrome/hyphen-data');


test.describe('Темы на телефоне: касания', () => {
  test('пользователь проводит пальцем, начав со плитки → экран прокручивается, ничего не открывается', async ({ page }) => {
    let x, y;
    let finger;

    await test.step('Дано экран тем, длиннее экрана', async () => {
      await openCollections(page);
      // Chrome on Android never sends the moves inside the touch slop, so the
      // phone's cancel arrives before any `pointermove`. Desktop Chrome sends
      // them; stopping them at the window reproduces the phone's stream.
      await recordPointer(page, ['pointermove']);
      finger = await touch(page);
      ({ x, y } = await centre(page.getByRole('button', { name: 'Alltag 0', exact: true })));
    });

    await test.step('Когда он ведёт палец вверх от плитки «Alltag»', async () => {
      await finger.down(x, y);
      for (let dy = 20; dy <= 300; dy += 20) await finger.move(x, y - dy);
      await finger.up();
      await nothingHappensFor(page, 300);
    });

    await test.step('Тогда он всё ещё на экране тем, а страница прокручена', async () => {
      // An activation would have gone home.
      await expect(page.getByRole('button', { name: 'Открыть наборы' })).toHaveCount(0);
      await expect(page.getByRole('heading', { name: 'Наборы' })).toBeAttached();
      // Without the cancel this would prove nothing about the recovery.
      expect(await page.evaluate(() => window.__pointer.cancels)).toBeGreaterThan(0);
      // The document is what scrolls on this screen.
      expect(await page.evaluate(() => document.scrollingElement.scrollTop)).toBeGreaterThan(100);
    });
  });

  test('пользователь касается плитки, не двигая пальцем → тема переключается, даже если браузер отменил касание', async ({ page }) => {
    const tile = page.getByRole('button', { name: 'Alltag 0', exact: true });

    await test.step('Дано экран тем', async () => {
      await openCollections(page);
    });

    await test.step('Когда он касается плитки «Alltag» и убирает палец', async () => {
      const finger = await touch(page);
      const { x, y } = await centre(tile);
      await finger.down(x, y);
      await finger.up();
    });

    await test.step('Тогда открыта тема «Alltag»', async () => {
      await expect(page.getByRole('heading', { name: 'Alltag', exact: true })).toBeVisible();
    });

    await test.step('Дано экран тем снова, браузер отменяет неподвижное касание', async () => {
      await page.getByRole('button', { name: 'Открыть наборы' }).click();
      await expect(tile).toBeVisible();
      // After a cancel Chrome sends no pointerup and no click; the real ones
      // this uncancelled touch produces are stopped at the window.
      await recordPointer(page, ['pointerup', 'click']);
    });

    await test.step('Когда он касается плитки, а браузер отменяет касание', async () => {
      const finger = await touch(page);
      const { x, y } = await centre(tile);
      // Chrome cancels a still finger when it decides the touch is its own —
      // the #404 case. The protocol cannot make it do so, so the cancel is the
      // one the browser would send, dispatched on the tile mid-touch.
      await finger.down(x, y);
      await tile.evaluate((el) => el.dispatchEvent(new PointerEvent('pointercancel', { bubbles: true, pointerType: 'touch' })));
      await finger.up();
    });

    await test.step('Тогда тема всё равно переключена', async () => {
      expect(await page.evaluate(() => window.__pointer.cancels)).toBe(1);
      await expect(page.getByRole('heading', { name: 'Alltag', exact: true })).toBeVisible();
    });
  });

  for (const { kind, name, count } of [
    { kind: 'строку папки', name: 'Meetings und Besprechungen mit Kollegen', count: '3' },
    { kind: 'заголовок папки', name: 'Reise', count: '3' },
    { kind: 'плитку без папки', name: 'Zusammenarbeitsvereinbarungen', count: '2' },
  ]) {
    test(`пользователь долго держит ${kind} → ✕ встаёт на место счётчика, имя не сдвигается`, async ({ page }) => {
      const button = page.getByRole('button', { name: `${name} ${count}`, exact: true });
      const nameEl = page.getByText(name, { exact: true });
      const countEl = nameEl.locator('xpath=following-sibling::span[1]');
      const close = page.getByRole('button', { name: `Удалить набор «${name}»` });
      let before;

      await test.step('Дано экран тем, счётчик виден', async () => {
        await openCollections(page);
        await expect(countEl).toHaveText(count);
        before = await nameEl.evaluate(layoutBox);
      });

      await test.step('Когда он долго держит палец', async () => {
        await longPress(page, button, close);
      });

      await test.step('Тогда счётчик прозрачен, но плитка на месте, имя не сдвинулось', async () => {
        // Transparent, not `visibility: hidden`: the count stays in the
        // target's accessible name.
        await expect(countEl).toHaveCSS('opacity', '0');
        await expect(button).toBeVisible();
        expect(await nameEl.evaluate(layoutBox)).toEqual(before);
      });

      await test.step('Тогда ✕ не перекрывает имя и стоит там, где был счётчик', async () => {
        const closeBox = await close.boundingBox();
        expect(overlaps(closeBox, await nameEl.boundingBox())).toBe(false);
        const digits = await countEl.evaluate(textRect);
        const mid = { x: digits.x + digits.width / 2, y: digits.y + digits.height / 2 };
        expect(mid.x).toBeGreaterThan(closeBox.x);
        expect(mid.x).toBeLessThan(closeBox.x + closeBox.width);
        expect(mid.y).toBeGreaterThan(closeBox.y);
        expect(mid.y).toBeLessThan(closeBox.y + closeBox.height);
      });
    });
  }

  // The keyboard's reveal is `:focus-visible`: the focus a touch delete hands
  // to the neighbour shows no ✕ of its own.
  test('пользователь нажимает на ✕ → тема удалена, других ✕ не появилось', async ({ page }) => {
    const button = page.getByRole('button', { name: 'Alltag 0', exact: true });
    const close = page.getByRole('button', { name: 'Удалить набор «Alltag»' });

    await test.step('Дано долгое нажатие на «Alltag» показало ✕', async () => {
      await openCollections(page);
      await longPress(page, button, close);
    });

    await test.step('Когда он касается ✕', async () => {
      const finger = await touch(page);
      const { x, y } = await centre(close);
      await finger.down(x, y);
      await finger.up();
    });

    await test.step('Тогда плитки «Alltag» нет, фокус у соседа, ни одного ✕ не видно', async () => {
      await expect(button).toHaveCount(0);
      await expect(page.locator('.masonry [data-collection-id]:focus')).toHaveCount(1);
      const shown = await page.locator('.tile__close').evaluateAll(
        (els) => els.filter((el) => getComputedStyle(el).opacity !== '0').length);
      expect(shown).toBe(0);
    });
  });

  test('пользователь открывает темы с длинными немецкими названиями → названия помещаются в плитки, длинные слова переносятся по слогам', async ({ page, baseURL }) => {
    await test.step('Дано экран тем с длинными названиями', async () => {
      await openCollections(page);
    });

    await test.step('Тогда немецкие названия помечены как немецкие, «Всё подряд» — нет', async () => {
      await expect(page.getByText('Unterkunftsmöglichkeiten', { exact: true })).toHaveAttribute('lang', 'de');
      await expect(page.getByText('Deutsch-Test', { exact: true })).toHaveAttribute('lang', 'de');
      await expect(page.getByText('Всё подряд', { exact: true })).not.toHaveAttribute('lang', /./);
      await expect(page.getByText('Unterkunftsmöglichkeiten', { exact: true })).toHaveCSS('hyphens', 'auto');
    });

    await test.step('Тогда строка без мест переноса всё равно умещается в плитку', async () => {
      const unbreakable = page.getByText('x'.repeat(40), { exact: true });
      const fits = await unbreakable.evaluate((el) => {
        const box = el.getBoundingClientRect();
        const tile = el.closest('.tile').getBoundingClientRect();
        return el.scrollWidth <= el.clientWidth && box.right <= tile.right && box.left >= tile.left;
      });
      expect(fits).toBe(true);
    });

    await test.step('Тогда длинное слово переносится только по слогам, с дефисом', async () => {
      if (!fs.existsSync(hyphenData)) {
        test.info().annotations.push({ type: 'skipped-step', description: 'no Chrome hyphenation data on this machine' });
        return;
      }
      const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'hyphen-'));
      fs.cpSync(hyphenData, path.join(dir, 'hyphen-data'), { recursive: true });
      const context = await chromium.launchPersistentContext(dir, {
        channel: 'chrome',
        headless: true,
        viewport: { width: 384, height: 800 },
        hasTouch: true,
        ignoreDefaultArgs: ['--disable-component-update'],
      });
      try {
        const hyphenated = context.pages()[0] || await context.newPage();
        await openCollections(hyphenated, baseURL);
        const word = hyphenated.getByText('Unterkunftsmöglichkeiten', { exact: true });
        // Un-ter-kunfts-mög-lich-kei-ten
        const syllables = [2, 5, 11, 14, 18, 21];
        const starts = await word.evaluate(lineStarts);
        expect(starts.length).toBeGreaterThan(0);
        for (const at of starts) expect(syllables).toContain(at);
      } finally {
        await context.close();
        fs.rmSync(dir, { recursive: true, force: true });
      }
    });
  });

  // Owner's report on #459: moving editing from one row to another made the
  // ✕ jump. A row in editing mode is 24 px wider (it reaches past the tile's
  // padding), and a ✕ placed from the row's edge moved 12 px while fading
  // out. Sampled every frame across the switch, each visible ✕ stays put.
  test('пользователь переносит правку с одной строки папки на другую → ни один ✕ не прыгает', async ({ page }) => {
    const fromLabel = 'Удалить набор «Meetings und Besprechungen mit Kollegen»';
    const toLabel = 'Удалить набор «E-Mails»';
    const from = page.getByRole('button', { name: 'Meetings und Besprechungen mit Kollegen 3', exact: true });
    const to = page.getByRole('button', { name: 'E-Mails 1', exact: true });
    const fromClose = page.getByRole('button', { name: fromLabel });
    const toClose = page.getByRole('button', { name: toLabel });
    let toRest, fromRest;

    await test.step('Дано правка включена на первой строке папки', async () => {
      await openCollections(page);
      // Where each ✕ stands at rest: the outgoing one in editing, the
      // incoming one before it is shown.
      toRest = await toClose.boundingBox();
      await longPress(page, from, fromClose);
      fromRest = await fromClose.boundingBox();
    });

    await test.step('Когда он долго держит вторую строку папки', async () => {
      await page.evaluate(() => {
        const closes = [...document.querySelectorAll('.tile__row .tile__close')];
        window.__frames = [];
        const sample = () => {
          window.__frames.push(closes.map((el) => {
            const r = el.getBoundingClientRect();
            return { label: el.getAttribute('aria-label'), x: r.x, y: r.y, opacity: Number(getComputedStyle(el).opacity) };
          }));
          if (window.__frames.length < 60) requestAnimationFrame(sample);
        };
        requestAnimationFrame(sample);
      });
      await longPress(page, to, toClose);
      await expect.poll(() => page.evaluate(() => window.__frames.length)).toBe(60);
    });

    await test.step('Тогда каждый ✕ в каждом кадре стоит на своём месте', async () => {
      const visible = (await page.evaluate(() => window.__frames)).flat().filter((c) => c.opacity > 0);
      const fromSeen = visible.filter((c) => c.label === fromLabel);
      const toSeen = visible.filter((c) => c.label === toLabel);
      // Both were caught mid-fade, or the sampling proves nothing.
      expect(fromSeen.some((c) => c.opacity < 1)).toBe(true);
      expect(toSeen.some((c) => c.opacity < 1)).toBe(true);
      for (const c of fromSeen) expect([c.x, c.y]).toEqual([fromRest.x, fromRest.y]);
      for (const c of toSeen) expect([c.x, c.y]).toEqual([toRest.x, toRest.y]);
    });
  });
});
