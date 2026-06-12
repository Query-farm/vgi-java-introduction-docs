// Single source of truth for the five function kinds: the order they're taught
// in, their brand colour, the cardinality formula their glyph draws, and a
// one-line gloss. KindIcon draws the shape, KindBanner heads each function page,
// and KindGallery lays all five out on the home page — all read from here so the
// colour, label, and wording can never drift apart.
export const KINDS = [
  {
    id: 'scalar',
    label: 'Scalar',
    color: '#2563eb',
    formula: '1 row → 1 value',
    desc: 'Runs on each row independently and returns a single value — a pure per-row transform.',
  },
  {
    id: 'table',
    label: 'Table',
    color: '#0d9488',
    formula: 'args → N rows',
    desc: 'A table-valued source: scalar arguments in, a whole set of rows out.',
  },
  {
    id: 'table-in-out',
    label: 'Table-in-out',
    color: '#7c3aed',
    formula: 'N rows → M rows',
    desc: 'Consumes a relation and streams a transformed relation back, batch by batch.',
  },
  {
    id: 'aggregate',
    label: 'Aggregate',
    color: '#d97706',
    formula: 'N rows → 1 value',
    desc: 'Folds many rows down into a single value per group.',
  },
  {
    id: 'buffering',
    label: 'Buffering',
    color: '#e11d48',
    formula: 'stream → [state] → stream',
    desc: 'Holds every input row in state before emitting — the basis for sorts and top-k.',
  },
]

export const KIND_COLORS = Object.fromEntries(KINDS.map((k) => [k.id, k.color]))
export const KIND_META = Object.fromEntries(KINDS.map((k) => [k.id, k]))
