const fs = require('fs');
const os = require('os');
const path = require('path');
const { test, expect, chromium } = require('@playwright/test');

// The issue's phone (#456): 384 × 800.
test.use({ viewport: { width: 384, height: 800 } });

// Enough tiles that the screen scrolls, and the names from the report.
const collections = [
  ['arbeit-meet', 'Arbeit/Meetings und Besprechungen mit Kollegen', ['vocab:a', 'vocab:b', 'vocab:c']],
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
  await page.getByRole('link', { name: 'Открыть наборы' }).click();
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

// Asserting that a swipe activated nothing: the recovered tap would dispatch
// inside the touchend handler, so there is nothing to wait for — only time
// to let pass before looking.
const nothingHappensFor = (page, ms) => page.waitForTimeout(ms);

test('a swipe that starts on a tile scrolls and activates nothing', async ({ page }) => {
  await openCollections(page);
  // Chrome on Android never sends the moves inside the touch slop, so the
  // phone's cancel arrives before any `pointermove`. Desktop Chrome sends
  // them; stopping them at the window reproduces the phone's stream.
  await recordPointer(page, ['pointermove']);
  const finger = await touch(page);
  const { x, y } = await centre(page.getByRole('button', { name: 'Alltag 0', exact: true }));

  await finger.down(x, y);
  for (let dy = 20; dy <= 300; dy += 20) await finger.move(x, y - dy);
  await finger.up();
  await nothingHappensFor(page, 300);

  // Still the themes screen: an activation would have gone home.
  await expect(page.getByRole('link', { name: 'Открыть наборы' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Наборы' })).toBeAttached();
  // Without the cancel this would prove nothing about the recovery.
  expect(await page.evaluate(() => window.__pointer.cancels)).toBeGreaterThan(0);
  // The document is what scrolls on this screen.
  expect(await page.evaluate(() => document.scrollingElement.scrollTop)).toBeGreaterThan(100);
});

test('a still tap switches the collection', async ({ page }) => {
  await openCollections(page);
  const finger = await touch(page);
  const { x, y } = await centre(page.getByRole('button', { name: 'Alltag 0', exact: true }));

  await finger.down(x, y);
  await finger.up();

  await expect(page.getByRole('heading', { name: 'Alltag', exact: true })).toBeVisible();
});

test('a still touch the browser cancels is still a tap', async ({ page }) => {
  await openCollections(page);
  // After a cancel Chrome sends no pointerup and no click; the real ones
  // this uncancelled touch produces are stopped at the window.
  await recordPointer(page, ['pointerup', 'click']);
  const finger = await touch(page);
  const tile = page.getByRole('button', { name: 'Alltag 0', exact: true });
  const { x, y } = await centre(tile);

  // Chrome cancels a still finger when it decides the touch is its own —
  // the #404 case. The protocol has no way to make it do so, so the cancel
  // is the one the browser would send, dispatched on the tile mid-touch.
  await finger.down(x, y);
  await tile.evaluate((el) => el.dispatchEvent(new PointerEvent('pointercancel', { bubbles: true, pointerType: 'touch' })));
  await finger.up();

  expect(await page.evaluate(() => window.__pointer.cancels)).toBe(1);
  await expect(page.getByRole('heading', { name: 'Alltag', exact: true })).toBeVisible();
});

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

for (const { kind, target, name, count, remove } of [
  { kind: 'row', target: 'Meetings und Besprechungen mit Kollegen 3', name: 'Meetings und Besprechungen mit Kollegen', count: '3', remove: 'Удалить набор «Meetings und Besprechungen mit Kollegen»' },
  { kind: 'folder header', target: 'Reise 3', name: 'Reise', count: '3', remove: 'Удалить набор «Reise»' },
  { kind: 'plain tile', target: 'Zusammenarbeitsvereinbarungen 2', name: 'Zusammenarbeitsvereinbarungen', count: '2', remove: 'Удалить набор «Zusammenarbeitsvereinbarungen»' },
]) {
  test(`in editing mode the ✕ on a ${kind} takes the count's place and leaves the name alone`, async ({ page }) => {
    await openCollections(page);
    const button = page.getByRole('button', { name: target, exact: true });
    // Found by their text, not through the button: a hidden count leaves
    // the button's accessible name.
    const nameEl = page.getByText(name, { exact: true });
    const countEl = nameEl.locator('xpath=following-sibling::span[1]');
    await expect(countEl).toHaveText(count);
    const before = await nameEl.evaluate(layoutBox);
    const close = page.getByRole('button', { name: remove, includeHidden: true });

    await longPress(page, button, close);

    await expect(countEl).toBeHidden();
    expect(await nameEl.evaluate(layoutBox)).toEqual(before);
    const closeBox = await close.boundingBox();
    expect(overlaps(closeBox, await nameEl.boundingBox())).toBe(false);
    // Where the count was: the ✕ box holds the centre of its digits.
    const digits = await countEl.evaluate(textRect);
    const mid = { x: digits.x + digits.width / 2, y: digits.y + digits.height / 2 };
    expect(mid.x).toBeGreaterThan(closeBox.x);
    expect(mid.x).toBeLessThan(closeBox.x + closeBox.width);
    expect(mid.y).toBeGreaterThan(closeBox.y);
    expect(mid.y).toBeLessThan(closeBox.y + closeBox.height);
  });
}

test('names are German, «Всё подряд» is not, and nothing overflows its tile', async ({ page }) => {
  await openCollections(page);
  await expect(page.getByText('Unterkunftsmöglichkeiten', { exact: true })).toHaveAttribute('lang', 'de');
  await expect(page.getByText('Deutsch-Test', { exact: true })).toHaveAttribute('lang', 'de');
  await expect(page.getByText('Всё подряд', { exact: true })).not.toHaveAttribute('lang', /./);
  await expect(page.getByText('Unterkunftsmöglichkeiten', { exact: true })).toHaveCSS('hyphens', 'auto');

  // A string with no hyphenation point still wraps inside its tile.
  const unbreakable = page.getByText('x'.repeat(40), { exact: true });
  const fits = await unbreakable.evaluate((el) => {
    const box = el.getBoundingClientRect();
    const tile = el.closest('.tile').getBoundingClientRect();
    return el.scrollWidth <= el.clientWidth && box.right <= tile.right && box.left >= tile.left;
  });
  expect(fits).toBe(true);
});

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

test('a long German word breaks at a syllable with a hyphen', async ({ baseURL }, testInfo) => {
  test.skip(!fs.existsSync(hyphenData), 'no Chrome hyphenation data on this machine');
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
    const page = context.pages()[0] || await context.newPage();
    await openCollections(page, baseURL);
    const word = page.getByText('Unterkunftsmöglichkeiten', { exact: true });
    // Un-ter-kunfts-mög-lich-kei-ten
    const syllables = [2, 5, 11, 14, 18, 21];
    const starts = await word.evaluate(lineStarts);
    expect(starts.length).toBeGreaterThan(0);
    for (const at of starts) expect(syllables).toContain(at);
    await word.screenshot({ path: testInfo.outputPath('hyphenated.png') });
  } finally {
    await context.close();
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
