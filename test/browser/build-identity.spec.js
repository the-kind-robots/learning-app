const { test, expect } = require('@playwright/test');
const { execFileSync } = require('node:child_process');

// A development build says which checkout it came from. The bundle under test
// was compiled from this working tree, so the build hook's short commit is the
// one git prints here; the date and time are only checked for shape, since the
// spec cannot know the minute the compile ran.
const shortCommit = execFileSync('git', ['rev-parse', '--short', 'HEAD'], { encoding: 'utf8' }).trim();
const stampPattern = new RegExp(`^${shortCommit}\\+? \\d\\d\\.\\d\\d \\d\\d:\\d\\d$`);

// Like the metrics specs: the shell renders and the instrumentation installs
// with the render component, which the runtime starts asynchronously, so a
// bare goto proves nothing.
// The word mark ends in a red D in a development build, so the button's name is
// "SprechaD" here and "Sprecha" in a release build.
const wordMark = (page) => page.getByRole('button', { name: /^Sprecha/ });

async function waitForShell(page) {
  await page.goto('/home');
  await expect(wordMark(page)).toBeVisible();
  await page.waitForFunction(() => typeof window.__trace === 'function');
}

// The export hands the JSON to the share sheet, the clipboard, or a prompt.
// Headless Chrome has no share sheet, so the clipboard is the arm that runs;
// this keeps what it was handed instead of writing it.
async function captureTheExport(page) {
  await page.addInitScript(() => {
    Object.defineProperty(navigator.clipboard, 'writeText', {
      value: async (text) => { window.__exported = text; },
    });
  });
}

test('the shell and the trace header name the build this page loaded', async ({ page }) => {
  await captureTheExport(page);
  await waitForShell(page);

  const mark = page.getByRole('button', { name: 'Перезагрузить сборку' });
  await expect(mark).toBeVisible();
  const stamp = (await mark.textContent()).trim();
  expect(stamp).toMatch(stampPattern);

  // The export is its own control in the actions row, so nothing reloads the
  // page off this build while it runs.
  await page.getByRole('button', { name: 'Экспортировать трассу' }).click();
  await page.waitForFunction(() => typeof window.__exported === 'string');
  const exported = JSON.parse(await page.evaluate(() => window.__exported));
  expect(exported.header.build).toBe(stamp);
});

// The bar's three slots share one line — word mark, build mark, actions — so
// the build mark is the one that could cover a neighbour or shove it aside.
// 384 px is where the bar is tightest; 1280 px says the middle slot is centred
// in the bar rather than midway between its neighbours.
for (const width of [384, 1280]) {
  test(`the top bar keeps its three slots apart at ${width} px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await waitForShell(page);

    const mark = await page.getByRole('button', { name: 'Перезагрузить сборку' }).boundingBox();
    const logo = await wordMark(page).boundingBox();
    // The leftmost and the rightmost member of the actions row on the home page.
    const firstAction = await page.getByRole('button', { name: 'Экспортировать трассу' }).boundingBox();
    const lastAction = await page.getByRole('button', { name: 'Открыть наборы' }).boundingBox();
    // Where a fixed `right: 0` lands, which is what the row's own `right: 16px`
    // was measured from before the bar held it: the viewport minus whatever
    // gutter the scrollbar reserves, and not `clientWidth`, which ignores it.
    const fixedRight = await page.evaluate(() => {
      const probe = document.createElement('div');
      probe.style.cssText = 'position:fixed;right:0;top:0;width:0;height:0';
      document.body.appendChild(probe);
      const right = probe.getBoundingClientRect().right;
      probe.remove();
      return right;
    });

    // The word mark starts where it did, so the build mark did not push it.
    expect(logo.x).toBe(16);
    // No overlap, left to right.
    expect(logo.x + logo.width).toBeLessThanOrEqual(mark.x);
    expect(mark.x + mark.width).toBeLessThanOrEqual(firstAction.x);
    // The actions row keeps its right margin.
    expect(lastAction.x + lastAction.width).toBeCloseTo(fixedRight - 16, 1);
    // Centred in the bar, not midway between the neighbours: the bar runs from
    // the word mark's left edge to the actions row's right edge.
    const barCentre = (logo.x + lastAction.x + lastAction.width) / 2;
    expect(Math.abs(mark.x + mark.width / 2 - barCentre)).toBeLessThanOrEqual(1);
  });
}
