const { test } = require('@playwright/test');
const tray = require('./words-tray.shared');

test('the words search sits above the lesson button, the rows under the bar', async ({ page }) => {
  await tray.openWordsWithAWord(page);
  await tray.expectSearchAboveTheButton(page);
  await tray.expectAirUnderTheBar(page);
});
