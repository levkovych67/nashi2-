import { MapPin } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatPartialDate, locationBasisNote } from '../utils/lifeDates';
import type { components } from '@/lib/api/generated/types';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

interface Fact {
  label: string;
  value: string;
}

function factsOf(artist: ArtistViewDTO): Fact[] {
  const categories = artist.categories ?? [];
  const isBand = categories.includes('BAND');
  const facts: Fact[] = [];

  const birth = [formatPartialDate(artist.birthDate), artist.birthPlace].filter(Boolean).join(', ');
  if (birth) facts.push({ label: 'Народження', value: birth });
  const death = formatPartialDate(artist.deathDate);
  if (death) facts.push({ label: 'Смерть', value: death });

  if (artist.foundationYear) {
    facts.push({ label: isBand ? 'Засновано' : 'Початок діяльності', value: String(artist.foundationYear) });
  }
  if (artist.disbandYear) {
    facts.push({ label: isBand ? 'Розпад' : 'Кінець діяльності', value: String(artist.disbandYear) });
  }

  const genres = (artist.genres ?? []).filter((g) => g.trim());
  if (genres.length > 0) {
    const plays = isBand || categories.includes('MUSICIAN');
    const paintsOnly = categories.includes('VISUAL_ARTIST') && !plays && !categories.includes('WRITER');
    facts.push({ label: paintsOnly ? 'Напрями' : 'Жанри', value: genres.join(', ') });
  }
  return facts;
}

/** Birth and death, founding and break-up, genres, and what the pin on the map stands for. */
export function ArtistFacts({ artist }: { artist: ArtistViewDTO }) {
  const facts = factsOf(artist);
  const mapNote = artist.city ? locationBasisNote(artist.locationBasis, artist.city) : null;
  if (facts.length === 0 && !mapNote) return null;

  return (
    <section aria-label="Факти" className="aero-groupbox mb-8 px-4 py-4 md:px-5">
      {facts.length > 0 && (
        <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
          {facts.map((fact) => (
            <div key={fact.label} className="min-w-0">
              <dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{fact.label}</dt>
              <dd className="mt-0.5 break-words">{fact.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {mapNote && (
        <p
          className={cn(
            'flex items-start gap-1.5 text-sm text-muted-foreground',
            facts.length > 0 && 'mt-3 border-t border-[#c4d7ed] pt-3',
          )}
        >
          <MapPin className="mt-0.5 h-4 w-4 flex-none" aria-hidden="true" />
          {mapNote}
        </p>
      )}
    </section>
  );
}
