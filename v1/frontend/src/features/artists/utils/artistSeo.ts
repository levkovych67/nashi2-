// Search-engine description and schema.org JSON-LD for an artist page: MusicGroup for bands, Person for everyone else.
import { CATEGORY_LABEL } from './lifeDates.ts';
import { isWebUrl } from '../../../lib/utils/isWebUrl.ts';

export interface SeoArtist {
  name?: string;
  biography?: string;
  pressRelease?: string;
  categories?: string[];
  genres?: string[];
  birthDate?: string;
  deathDate?: string;
  birthPlace?: string;
  foundationYear?: number;
  disbandYear?: number;
  city?: string;
  imageUrls?: string[];
  wikiUrl?: string;
  wikidataUrl?: string;
  socialLinks?: { platform?: string; url?: string }[];
}

const DESCRIPTION_LENGTH = 160;

/** The first 160 characters of the biography (or the press release), on one line. */
export function seoDescription(a: Pick<SeoArtist, 'biography' | 'pressRelease'>): string | undefined {
  const text = (a.biography || a.pressRelease || '').replace(/\s+/g, ' ').trim();
  return text ? Array.from(text).slice(0, DESCRIPTION_LENGTH).join('') : undefined;
}

/** Drops empty values so the JSON-LD carries only what we know. */
function compact(obj: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v != null && v !== '' && !(Array.isArray(v) && v.length === 0)),
  );
}

const place = (name: string | undefined) => (name ? { '@type': 'Place', name } : undefined);

export function artistJsonLd(a: SeoArtist): Record<string, unknown> {
  const isBand = a.categories?.includes('BAND') ?? false;
  const common = {
    '@context': 'https://schema.org',
    '@type': isBand ? 'MusicGroup' : 'Person',
    name: a.name,
    description: seoDescription(a),
    image: a.imageUrls?.[0],
    sameAs: [...new Set([a.wikiUrl, a.wikidataUrl, ...(a.socialLinks ?? []).map((l) => l.url)].filter(isWebUrl))],
  };
  if (isBand) {
    return compact({
      ...common,
      genre: a.genres,
      foundingDate: a.foundationYear?.toString(),
      dissolutionDate: a.disbandYear?.toString(),
      location: place(a.city),
    });
  }
  const jobTitles = (a.categories ?? [])
    .map((c) => (Object.hasOwn(CATEGORY_LABEL, c) ? CATEGORY_LABEL[c] : undefined))
    .filter((t): t is string => !!t);
  return compact({
    ...common,
    jobTitle: jobTitles.length > 1 ? jobTitles : jobTitles[0],
    birthDate: a.birthDate,
    deathDate: a.deathDate,
    birthPlace: place(a.birthPlace),
  });
}
