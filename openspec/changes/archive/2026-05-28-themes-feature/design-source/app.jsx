/* App — persistent top header + content area that swaps between
   HomePage and Switcher. Zoom transition expands a tapped card into
   the content area only (the header stays put). */

const { useState, useRef, useEffect, useMemo } = React;

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "green"
}/*EDITMODE-END*/;

const ACCENTS = {
  green:  { primary: '#58cc02', deep: '#46a302' },
  blue:   { primary: '#1cb0f6', deep: '#1899d6' },
  purple: { primary: '#ce82ff', deep: '#a560cc' },
};

/* Duolingo palette — assigned to cards in a fixed cycle so each set has
   a stable, distinguishable border color without the system pretending
   to "theme" anything. */
const CARD_PALETTE = [
  '#58cc02', // green
  '#1cb0f6', // blue
  '#ff9600', // orange
  '#ff86d0', // pink
  '#ce82ff', // purple
  '#ffc800', // yellow
  '#ff4b4b', // red
  '#00cd9c', // teal
];

const INITIAL_SETS = [
  /* Main card — fixed name "Всё подряд", undeletable, can't be renamed.
     Acts as the catch-all for users who don't engage with sets at all. */
  { id: 'main', name: 'Всё подряд', borderColor: CARD_PALETTE[0],
    words: [
      ['der Zweifel',  'сомнение'],
      ['der Hund',     'собака'],
      ['laufen',       'бегать'],
      ['schwimmen',    'плавать'],
      ['das Spiel',    'игра'],
      ['der Apfel',    'яблоко'],
      ['die Reise',    'поездка'],
      ['fliegen',      'летать'],
    ] },
  { id: 'reisen', name: 'Reisen', borderColor: CARD_PALETTE[1],
    words: [
      ['der Zug',      'поезд'],
      ['das Flugzeug', 'самолёт'],
      ['die Reise',    'поездка'],
      ['der Koffer',   'чемодан'],
      ['das Hotel',    'отель'],
      ['fliegen',      'летать'],
    ] },
  { id: 'work', name: 'на работе', borderColor: CARD_PALETTE[2],
    words: [
      ['das Büro',     'офис'],
      ['der Chef',     'начальник'],
      ['die Sitzung',  'совещание'],
      ['arbeiten',     'работать'],
      ['der Termin',   'встреча'],
    ] },
  { id: 'rand', name: 'тест 42', borderColor: CARD_PALETTE[3],
    words: [['echt', 'реально']] },
];

function MorphIcon({ open }) {
  const baseStyle = { position: 'absolute', inset: 0 };
  return (
    <div className="morph-icon" aria-hidden="true">
      <svg
        width="22" height="22" viewBox="0 0 24 24" fill="none"
        style={{ ...baseStyle, display: open ? 'none' : 'block' }}
      >
        <rect x="3"  y="3"  width="8" height="8" rx="2" fill="currentColor" />
        <rect x="13" y="3"  width="8" height="8" rx="2" fill="currentColor" />
        <rect x="3"  y="13" width="8" height="8" rx="2" fill="currentColor" />
        <rect x="13" y="13" width="8" height="8" rx="2" fill="currentColor" />
      </svg>
      <svg
        width="22" height="22" viewBox="0 0 24 24" fill="none"
        style={{ ...baseStyle, display: open ? 'block' : 'none' }}
      >
        <path d="M6 6 L18 18 M18 6 L6 18"
              stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"/>
      </svg>
    </div>
  );
}

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [view, setView] = useState('home'); // 'home' | 'switcher'
  const [sets, setSets] = useState(INITIAL_SETS);
  const [activeId, setActiveId] = useState('main');
  const [zoom, setZoom] = useState(null);

  useEffect(() => {
    const a = ACCENTS[t.accent] || ACCENTS.green;
    document.documentElement.style.setProperty('--accent', a.primary);
    document.documentElement.style.setProperty('--accent-deep', a.deep);
  }, [t.accent]);

  const contentRef = useRef(null);
  const activeSet = useMemo(() => sets.find((s) => s.id === activeId) || sets[0], [sets, activeId]);

  /* Mutations ------------------------------------------------------- */
  const addWord = (de, ru) => {
    setSets((cur) => cur.map((s) => s.id === activeId
      ? { ...s, words: [...s.words, [de, ru]] }
      : s));
  };
  const renameSet = (id, name) => {
    setSets((cur) => cur.map((s) => s.id === id ? { ...s, name } : s));
  };
  const deleteSet = (id) => {
    setSets((cur) => {
      if (cur.length <= 1) return cur;
      if (cur[0].id === id) return cur; // belt + braces: first is undeletable
      const next = cur.filter((s) => s.id !== id);
      if (id === activeId) setActiveId(next[0].id);
      return next;
    });
  };
  const reorderSet = (dragId, overId) => {
    setSets((cur) => {
      const from = cur.findIndex((s) => s.id === dragId);
      const to   = cur.findIndex((s) => s.id === overId);
      /* The first (main) card is locked to position 0 */
      if (from < 1 || to < 1 || from === to) return cur;
      const next = cur.slice();
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
  };
  const createSet = () => {
    const id = `set-${Date.now()}`;
    const set = {
      id,
      name: '',
      borderColor: CARD_PALETTE[sets.length % CARD_PALETTE.length],
      words: [],
    };
    setSets((cur) => [...cur, set]);
    // Open the new set immediately so the user can name it inline.
    setActiveId(id);
    setView('home');
  };

  /* View transitions ------------------------------------------------ */
  const openSwitcher = () => setView('switcher');
  const closeSwitcher = () => setView('home');
  const toggleView = () => setView((v) => v === 'home' ? 'switcher' : 'home');

  const pickSet = (id, cardEl) => {
    const content = contentRef.current?.getBoundingClientRect();
    const r = cardEl?.getBoundingClientRect();
    const set = sets.find((s) => s.id === id);
    if (cardEl && content && set) {
      setZoom({
        borderColor: set.borderColor,
        active: id === activeId,
        from: {
          top:    r.top    - content.top,
          left:   r.left   - content.left,
          width:  r.width,
          height: r.height,
        },
        expanded: false,
      });
      setActiveId(id);
      requestAnimationFrame(() => requestAnimationFrame(() => {
        setZoom((z) => z ? { ...z, expanded: true } : null);
        setView('home');
      }));
      setTimeout(() => setZoom(null), 520);
    } else {
      setActiveId(id);
      setView('home');
    }
  };

  return (
    <div className="app-shell">
      {/* Persistent header — never changes between views, never zoomed over */}
      <header className="topbar">
        <div className="brand">Sprecha</div>
        <button
          className="icon-btn"
          aria-label={view === 'switcher' ? 'Закрыть наборы' : 'Открыть наборы'}
          onClick={toggleView}
        >
          <MorphIcon open={view === 'switcher'} />
        </button>
      </header>

      <main className="content" ref={contentRef}>
        {view === 'home' && (
          <div key={activeId} className="view-enter" style={{ position: 'absolute', inset: 0 }}>
            <HomePage
              set={activeSet}
              onAddWord={addWord}
              onRenameSet={(name) => renameSet(activeSet.id, name)}
              namePlaceholder={activeSet.id === 'main' ? '' : 'Без названия'}
            />
          </div>
        )}

        {view === 'switcher' && (
          <Switcher
            sets={sets}
            activeId={activeId}
            onPick={pickSet}
            onDelete={deleteSet}
            onRename={renameSet}
            onCreate={createSet}
            onReorder={reorderSet}
          />
        )}

        {zoom && (
          <div
            className="zoom-overlay"
            style={zoom.expanded ? {
              top: 0, left: 0, width: '100%', height: '100%',
              borderRadius: 0,
              borderWidth: 0,
              borderColor: 'transparent',
              opacity: 0,
            } : {
              top:    zoom.from.top,
              left:   zoom.from.left,
              width:  zoom.from.width,
              height: zoom.from.height,
              borderRadius: 20,
              borderWidth: zoom.active ? 3 : 2,
              borderColor: zoom.borderColor,
              opacity: 1,
            }}
          />
        )}
      </main>

      <TweaksUI t={t} setTweak={setTweak} />
    </div>
  );
}

function TweaksUI({ t, setTweak }) {
  const accentOpts = Object.keys(ACCENTS).map((k) => ACCENTS[k].primary);
  return (
    <TweaksPanel title="Tweaks">
      <TweakSection label="Бренд" />
      <TweakColor
        label="Акцент (кнопка «Начать урок»)"
        value={ACCENTS[t.accent]?.primary}
        options={accentOpts}
        onChange={(hex) => {
          const k = Object.keys(ACCENTS).find((key) => ACCENTS[key].primary === hex) || 'green';
          setTweak('accent', k);
        }}
      />
    </TweaksPanel>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
