const { test, expect } = require('@playwright/test');
const tray = require('./words-tray.shared');

test('on a phone the search sits above the lesson button, the rows under the bar', async ({ page }) => {
  await tray.openWordsWithAWord(page);
  await tray.expectSearchAboveTheButton(page);
  await tray.expectAirUnderTheBar(page);
});

// Only the closed keyboard is measured here. Headless Chrome has no keyboard
// and so no `keyboard-inset-height` to give; the field on the keyboard and
// the button behind it are checked on a phone.
test('the screen asks for the keyboard overlay and fixes the lesson button to the bottom', async ({ page }) => {
  await tray.openWordsWithAWord(page);

  const overlays = await page.evaluate(() =>
    'virtualKeyboard' in navigator ? navigator.virtualKeyboard.overlaysContent : null);
  expect(overlays).toBe(true);

  const footer = page.getByRole('contentinfo').filter({ has: tray.lessonButton(page) });
  const { position, bottom } = await footer.evaluate((el) => ({
    position: getComputedStyle(el).position,
    bottom: el.getBoundingClientRect().bottom,
  }));
  expect(position).toBe('fixed');
  expect(bottom).toBe(page.viewportSize().height);
});
