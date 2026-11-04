const { test, expect } = require('./fixtures');

// Three words never answered and three answered three days ago, written straight
// into the user database (README, "Seeding from a spec"). Each is told apart by
// its translation, which is the lesson's prompt.
const words = [
  ['der Hund', 'новое один'], ['die Katze', 'новое два'], ['das Pferd', 'новое три'],
  ['der Tisch', 'знакомое один'], ['die Lampe', 'знакомое два'], ['das Fenster', 'знакомое три'],
];

async function seedVocabulary(page) {
  await page.evaluate(async (words) => {
    const now = new Date();
    const daysAgo = (days) => new Date(now.getTime() - days * 86400000).toISOString();
    const docs = words.flatMap(([value, translation], i) => {
      const vocab = {
        _id: 'vocab:' + value, type: 'vocab', value, translation: [{ lang: 'ru', value: translation }],
        created_at: daysAgo(10), modified_at: daysAgo(10),
      };
      return i < 3
        ? [vocab]
        : [vocab, { _id: 'review-' + i, type: 'review', word_id: vocab._id, retained: true, created_at: daysAgo(3) }];
    });
    await db.bulk_docs(db.use('user-db'), docs);
  }, words);
}

test.describe('Выбор слов урока', () => {
  test('ученик с тремя новыми и тремя знакомыми словами проходит урок → в уроке одно новое слово', async ({ page }) => {
    await test.step('Дано три новых слова и три знакомых', async () => {
      await page.goto('/home');
      await seedVocabulary(page);
    });

    const prompts = [];
    await test.step('Когда он проходит урок, отвечая верно', async () => {
      await page.goto('/lesson');
      const answers = Object.fromEntries(words.map(([value, translation]) => [translation, value]));
      for (let trial = 0; trial < 3; trial++) {
        const prompt = page.locator('.lesson .prompt');
        await expect(prompt).toBeVisible();
        const text = (await prompt.textContent()).trim();
        prompts.push(text);
        await page.getByRole('textbox', { name: 'Ответ на немецком' }).fill(answers[text]);
        await page.getByRole('button', { name: 'ПРОВЕРИТЬ' }).click();
        await expect(page.getByRole('heading', { name: 'Правильно!' })).toBeVisible();
        await page.getByRole('button', { name: trial < 2 ? 'ДАЛЕЕ' : 'ЗАКОНЧИТЬ' }).click();
      }
    });

    await test.step('Тогда из трёх слов урока новое — одно', async () => {
      expect(new Set(prompts).size).toBe(3);
      expect(prompts.filter((text) => text.startsWith('новое'))).toHaveLength(1);
    });
  });
});
