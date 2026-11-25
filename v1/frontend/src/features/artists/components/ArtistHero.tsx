import { Link } from 'react-router-dom';
import { Calendar, Feather, MapPin, MicVocal, Palette, Users, type LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { CATEGORY_LABEL, lifeSpan } from '../utils/lifeDates';
import type { components } from '@/lib/api/generated/types';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

const CATEGORY_ICON: Record<string, LucideIcon> = {
  MUSICIAN: MicVocal,
  BAND: Users,
  VISUAL_ARTIST: Palette,
  WRITER: Feather,
};

/** Up to two initials, for an artist without a photo. */
function initialsOf(name: string): string {
  const letters = name
    .split(/\s+/)
    .map((word) => word.match(/\p{L}/u)?.[0])
    .filter((letter): letter is string => !!letter);
  return letters.slice(0, 2).join('').toUpperCase() || '?';
}

/**
 * The photo band above the card. Most photos are portraits, which a full-width crop cuts to a forehead, so the whole
 * photo stands over a blurred copy of itself. When the photo fails to load, `onError` lets the page drop the band and
 * show the initials instead.
 */
export function ArtistHeroPhoto({ src, alt, onError }: { src: string; alt: string; onError: () => void }) {
  return (
    <div className="relative h-[50vh] w-full overflow-hidden bg-accent/10 md:h-[60vh]">
      <img
        src={src}
        alt=""
        aria-hidden="true"
        className="absolute inset-0 h-full w-full scale-110 object-cover opacity-80 blur-2xl"
      />
      <img
        src={src}
        alt={alt}
        decoding="async"
        onError={onError}
        className="relative mx-auto block h-full w-auto max-w-full object-contain"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-background via-background/50 to-transparent" />
    </div>
  );
}

/** Name, kinds, years and place of the artist, and the way to the map. */
export function ArtistHero({ artist, hasPhoto }: { artist: ArtistViewDTO; hasPhoto: boolean }) {
  const name = artist.name ?? '';
  const categories = (artist.categories ?? []).filter((c) => Object.hasOwn(CATEGORY_LABEL, c));
  const years = lifeSpan(artist);
  const place = artist.birthPlace || artist.city;
  const onMap = artist.latitude != null && artist.longitude != null && !!artist.slug;
  // Imported artists carry English genre tags; their genres are shown, in Ukrainian, among the facts.
  const tags = artist.source === 'WIKIPEDIA' ? [] : (artist.musicTags ?? []);

  return (
    <header className="mb-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          {!hasPhoto && (
            <span
              className="aero-orb flex h-16 w-16 flex-none items-center justify-center text-2xl font-semibold md:h-20 md:w-20 md:text-3xl"
              aria-hidden="true"
            >
              <span className="relative z-[2] [text-shadow:0_1px_2px_rgba(0,30,70,0.7)]">{initialsOf(name)}</span>
            </span>
          )}
          <div className="min-w-0">
            <h1 className="mb-3 break-words text-4xl font-heading font-bold md:text-5xl">{name}</h1>
            {categories.length > 0 && (
              <ul className="mb-3 flex flex-wrap gap-2" aria-label="Тип">
                {categories.map((category) => {
                  const Icon = CATEGORY_ICON[category];
                  return (
                    <li key={category} className="aero-tag gap-1.5 text-sm">
                      {Icon && <Icon className="h-3.5 w-3.5" aria-hidden="true" />}
                      {CATEGORY_LABEL[category]}
                    </li>
                  );
                })}
              </ul>
            )}
            {(years || place) && (
              <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                {years && (
                  <span className="inline-flex items-center gap-1.5">
                    <Calendar className="h-4 w-4" aria-hidden="true" />
                    {years}
                  </span>
                )}
                {place && (
                  <span className="inline-flex items-center gap-1.5">
                    <MapPin className="h-4 w-4" aria-hidden="true" />
                    {place}
                  </span>
                )}
              </p>
            )}
          </div>
        </div>
        {onMap && (
          <Button variant="outline" asChild className="shrink-0 self-start">
            <Link to={'/?artist=' + encodeURIComponent(artist.slug!)}>
              <MapPin aria-hidden="true" />
              Показати на карті
            </Link>
          </Button>
        )}
      </div>
      {tags.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label="Теги">
          {tags.map((tag) => (
            <li key={tag} className="aero-tag">
              {tag}
            </li>
          ))}
        </ul>
      )}
    </header>
  );
}
