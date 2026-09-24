const { test } = require('@playwright/test');
const { setUpLesson, expectHintedAnswerAsWideAsPlainText } = require('./lesson-answer.shared');

// The same width check as the desktop spec, at phone width, where the
// revealed answer sits in the narrow footer under the typed one (#409).
test('on a phone hinted words take the width of plain text', async ({ page }) => {
  await setUpLesson(page, 'Der Hund schläft im Garten.');
  await expectHintedAnswerAsWideAsPlainText(page);
});
