// Partial ISO dates (year, year-month, full) and life-span text for artist pages.
const MONTHS_GENITIVE = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
const MONTHS_NOMINATIVE = ['січень', 'лютий', 'березень', 'квітень', 'травень', 'червень', 'липень', 'серпень', 'вересень', 'жовтень', 'листопад', 'грудень'];

export const CATEGORY_LABEL: Record<string, string> = {
  MUSICIAN: 'Музикант',
  BAND: 'Гурт',
  VISUAL_ARTIST: 'Художник',
  WRITER: 'Письменник',
};

export function formatPartialDate(iso?: string | null): string | null {
  if (!iso) return null;
  const m = iso.match(/^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?$/);
  if (!m) return null;
  const [, year, month, day] = m;
  if (!month) return year;
  const idx = Number(month) - 1;
  if (idx < 0 || idx > 11) return year;
  if (!day) return `${MONTHS_NOMINATIVE[idx]} ${year}`;
  return `${Number(day)} ${MONTHS_GENITIVE[idx]} ${year}`;
}

const yearOf = (iso?: string | null) => (iso ? iso.slice(0, 4) : null);

export function lifeSpan(a: {
  categories?: string[];
  birthDate?: string | null;
  deathDate?: string | null;
  foundationYear?: number | null;
  disbandYear?: number | null;
}): string | null {
  if (a.categories?.includes('BAND')) {
    if (a.foundationYear && a.disbandYear) return `${a.foundationYear}–${a.disbandYear}`;
    if (a.foundationYear) return `з ${a.foundationYear}`;
    return null;
  }
  const b = yearOf(a.birthDate);
  const d = yearOf(a.deathDate);
  if (b && d) return `${b}–${d}`;
  if (b) return `нар. ${b}`;
  if (d) return `пом. ${d}`;
  return null;
}

export function locationBasisNote(basis: string | null | undefined, city: string): string | null {
  switch (basis) {
    case 'RESIDENCE': return `На карті: ${city} — місце життя`;
    case 'WORK': return `На карті: ${city} — місце роботи`;
    case 'FORMATION': return `На карті: ${city} — місце заснування`;
    default: return null;
  }
}
