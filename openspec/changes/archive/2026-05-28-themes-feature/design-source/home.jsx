/* HomePage — primary "add a word" surface for the active set.
   - Large editable title (set name; fixed "Всё подряд" on the main card)
   - Add-word card with two inputs and a submit
   - Sticky "Начать урок" CTA

   The full list of words for the set is intentionally NOT rendered here;
   it lives behind a "Список слов" button (out of scope for this proto)
   and appears as a preview inside each switcher card. */

const { useState: useStateH, useRef: useRefH } = React;

/* Stable per-word progress color used by the card preview. Hash by
   German text so each word keeps the same color across re-renders. */
const PROGRESS_DOT_COLORS = ['#ff4b4b', '#ff9600', '#ffc800', '#58cc02'];
function progressDotColor(de) {
  let h = 0;
  for (let i = 0; i < de.length; i++) h = (h * 31 + de.charCodeAt(i)) | 0;
  return PROGRESS_DOT_COLORS[Math.abs(h) % PROGRESS_DOT_COLORS.length];
}

function HomePage({ set, onAddWord, onRenameSet, namePlaceholder = '', interactive = true }) {
  const [w, setW] = useStateH('');
  const [t, setT] = useStateH('');
  const newWordRef = useRefH(null);

  const isMain = set?.id === 'main';
  const nameDisabled = !interactive || isMain;

  const submit = (e) => {
    e?.preventDefault();
    if (!interactive) return;
    if (!w.trim() || !t.trim()) return;
    onAddWord?.(w.trim(), t.trim());
    setW(''); setT('');
    newWordRef.current?.focus();
  };

  return (
    <div className="home">
      <div className="home-scroll">
        {!isMain && (
          <input
            className="set-name"
            value={set?.name ?? ''}
            placeholder={namePlaceholder}
            onChange={(e) => onRenameSet?.(e.target.value)}
            disabled={nameDisabled}
            spellCheck={false}
            aria-label="Название набора"
          />
        )}

        <div className="add-card">
          <div className="section-head">
            <h2>Добавить слово</h2>
            <button className="pill-btn" type="button" tabIndex={interactive ? 0 : -1}>
              Список слов
            </button>
          </div>

          <form className="word-row-form" onSubmit={submit}>
            <input
              ref={newWordRef}
              className="t-input"
              placeholder="Новое слово"
              value={w}
              onChange={(e) => setW(e.target.value)}
              disabled={!interactive}
              aria-label="Новое слово"
            />
            <div className="arrow">→</div>
            <input
              className="t-input"
              placeholder="Перевод"
              value={t}
              onChange={(e) => setT(e.target.value)}
              disabled={!interactive}
              aria-label="Перевод"
            />
            <button type="submit" className="duo-btn" style={{ gridColumn: '1 / -1' }}>
              Добавить
            </button>
          </form>
        </div>
      </div>

      <div className="home-footer">
        <button className="duo-btn primary lg" disabled={!interactive}>
          Начать урок
        </button>
      </div>
    </div>
  );
}

Object.assign(window, { HomePage, progressDotColor });
