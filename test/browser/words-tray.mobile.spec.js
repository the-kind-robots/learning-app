const { test, expect } = require('@playwright/test');
const tray = require('./words-tray.shared');

test('on a phone the search sits above the lesson button, the rows under the bar', async ({ page }) => {
  await tray.openWordsWithAWord(page);
  await tray.expectSearchAboveTheButton(page);
  await tray.expectAirUnderTheBar(page);
});

// An emulated keyboard: headless Chrome has none, so the viewport is shrunk
// the way `interactive-widget=resizes-content` shrinks it when one opens.
// What a real keyboard does on a device is not proven here.
test('with the viewport shrunk as by a keyboard, the tray stays on screen', async ({ page }) => {
  await tray.openWordsWithAWord(page);
  // The screen does not ask Chrome to lay the keyboard over the content, so
  // Chrome resizes the viewport for it — what the lesson relies on.
  const overlays = await page.evaluate(() =>
    'virtualKeyboard' in navigator ? navigator.virtualKeyboard.overlaysContent : false);
  expect(overlays).toBe(false);

  await tray.searchBox(page).click();
  await page.keyboard.type('Ha');
  await page.setViewportSize({ width: 390, height: 500 });

  await expect(tray.searchBox(page)).toBeInViewport({ ratio: 1 });
  await expect(tray.lessonButton(page)).toBeInViewport({ ratio: 1 });
  await expect(page.getByPlaceholder('Поиск')).toBeFocused();
  await tray.expectSearchAboveTheButton(page);
});
