/* Switcher — bare grid of cards.
   Card lifecycle:
     idle       — tap opens the card
     editing    — long-press enters this state on a single card; shows
                  its X (delete) button and allows drag-reorder.
                  Any tap not on the X exits editing.
   Renaming is NOT available on the switcher — it lives on the home page. */

const { useState: useStateS, useEffect: useEffectS, useRef: useRefS } = React;

/* Card preview — mirrors the home page's list body, but where the search
   bar would be, we render the set name in a pill. No add-word form, no
   lesson button — the card is a content preview, not a control surface. */
function CardPreview({ set }) {
  const words = set.words || [];
  const isEmpty = words.length === 0;
  const isMain = set.id === 'main';
  const shown = words.slice(0, 14);

  const namePlaceholder = isMain ? '' : 'Без названия';
  const name = set.name || namePlaceholder;

  return (
    <div className="card-preview">
      <div className={`cp-name ${!set.name ? 'is-placeholder' : ''}`}>
        {name}
      </div>

      {isEmpty ? (
        <div className="cp-empty" aria-hidden="true">
          <div className="cp-skel-row" />
          <div className="cp-skel-row" />
          <div className="cp-skel-row" />
        </div>
      ) : (
        <ul className="cp-list">
          {shown.map(([de, ru], i) => (
            <li key={`${de}-${i}`}>
              <span className="cp-dot" style={{ background: progressDotColor(de) }} />
              <span className="cp-de">{de}</span>
              <span className="cp-ru">{ru}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TabCard({
  set, isActive, isUndeletable, editing,
  onClick, onPointerDown, onDelete,
  isDragging, draggingY,
}) {
  return (
    <div
      className={`tab-card ${isActive ? 'active' : ''} ${editing ? 'editing' : ''} ${isDragging ? 'dragging' : ''}`}
      data-set-id={set.id}
      style={{
        '--card-border': set.borderColor,
        ...(isDragging ? { transform: `translateY(${draggingY}px) scale(1.04)`, zIndex: 20 } : null),
      }}
      onClick={onClick}
      onPointerDown={onPointerDown}
    >
      {!isUndeletable && editing && (
        <button
          className="close"
          onClick={(e) => { e.stopPropagation(); onDelete?.(); }}
          aria-label="Удалить набор"
        >
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M2 2 L10 10 M10 2 L2 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
        </button>
      )}

      <CardPreview set={set} />
    </div>
  );
}

function NewSetCard({ onCreate }) {
  return (
    <button
      className="tab-card new"
      onClick={onCreate}
      aria-label="Новый набор"
      type="button"
    >
      <svg className="dash-frame" preserveAspectRatio="none" viewBox="0 0 90 160">
        <rect x="1" y="1" width="88" height="158"
              rx="10" ry="10"
              fill="none"
              stroke="rgba(60,60,60,0.3)"
              strokeWidth="1.2"
              strokeDasharray="7 4"
              vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="plus" aria-hidden="true">
        <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
          <path d="M20 8 V32 M8 20 H32"
                stroke="currentColor"
                strokeWidth="3.5"
                strokeLinecap="round"/>
        </svg>
      </div>
    </button>
  );
}

function Switcher({
  sets, activeId, onPick, onDelete, onCreate, onReorder,
}) {
  const [editingId, setEditingId] = useStateS(null);
  const [dragState, setDragState] = useStateS(null);
  const gridRef = useRefS(null);

  /* Long-press + drag interaction model
     - pointerdown on card: arm long-press timer (unless card is already
       editing — then drag is immediately available).
     - move > 10px BEFORE timer fires: cancel timer (user is scrolling).
     - timer fires (500ms): enter editing on this card; arm drag.
     - move while armed: enter drag mode; reorder by hit-test.
     - pointerup: clear timer/drag. If long-press fired, suppress the
       click that fires next so it doesn't immediately exit editing. */
  const longPressTimer = useRefS(null);
  const longPressFired = useRefS(false);
  const drag = useRefS(null);

  const onCardPointerDown = (id) => (e) => {
    longPressFired.current = false;
    drag.current = {
      id,
      startX: e.clientX,
      startY: e.clientY,
      armed: editingId === id, // already editing → drag is immediately armed
    };

    if (editingId !== id) {
      longPressTimer.current = setTimeout(() => {
        longPressTimer.current = null;
        longPressFired.current = true;
        setEditingId(id);
        if (drag.current) drag.current.armed = true;
      }, 500);
    }

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp, { once: true });
    window.addEventListener('pointercancel', onUp, { once: true });
  };

  const onMove = (e) => {
    const d = drag.current;
    if (!d) return;
    const dy = e.clientY - d.startY;
    const dist = Math.hypot(e.clientX - d.startX, dy);

    // Cancel long-press if the user starts scrolling before it fires
    if (longPressTimer.current && dist > 10) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
      d.armed = false;
      return;
    }

    if (!d.armed || dist < 4) return;
    setDragState({ id: d.id, dy });

    const grid = gridRef.current;
    if (!grid) return;
    const cards = [...grid.querySelectorAll('.tab-card[data-set-id]')];
    const overEl = cards.find((c) => {
      const r = c.getBoundingClientRect();
      return e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom;
    });
    if (overEl) {
      const overId = overEl.dataset.setId;
      if (overId && overId !== d.id) onReorder(d.id, overId);
    }
  };

  const onUp = () => {
    window.removeEventListener('pointermove', onMove);
    if (longPressTimer.current) { clearTimeout(longPressTimer.current); longPressTimer.current = null; }
    drag.current = null;
    setDragState(null);
  };

  /* Click handlers ------------------------------------------------- */
  const handleCardClick = (id) => (e) => {
    // If this click is the tail of a long-press, swallow it — long-press
    // already entered editing mode.
    if (longPressFired.current) { longPressFired.current = false; return; }
    // Any short tap that lands while a card is in editing mode exits
    // editing mode and does NOT open the card.
    if (editingId !== null) { setEditingId(null); return; }
    onPick(id, e.currentTarget);
  };

  // Tap on switcher background (outside any card) also exits editing
  const handleSwitcherClick = (e) => {
    if (e.target === e.currentTarget && editingId !== null) setEditingId(null);
  };

  /* Escape also exits editing */
  useEffectS(() => {
    const h = (ev) => { if (ev.key === 'Escape' && editingId !== null) setEditingId(null); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [editingId]);

  return (
    <div className="switcher view-enter" onClick={handleSwitcherClick}>
      <div ref={gridRef} className="cards-grid">
        {sets.map((s, idx) => (
          <TabCard
            key={s.id}
            set={s}
            isActive={s.id === activeId}
            isUndeletable={idx === 0}
            editing={editingId === s.id}
            onClick={handleCardClick(s.id)}
            onPointerDown={onCardPointerDown(s.id)}
            onDelete={() => { onDelete(s.id); setEditingId(null); }}
            isDragging={dragState?.id === s.id}
            draggingY={dragState?.id === s.id ? dragState.dy : 0}
          />
        ))}
        <NewSetCard onCreate={() => { setEditingId(null); onCreate(); }} />
      </div>
    </div>
  );
}

Object.assign(window, { Switcher, TabCard, NewSetCard });
