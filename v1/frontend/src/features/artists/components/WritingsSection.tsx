import { READING_PLATFORMS, groupLinks, hasWritingsSection } from '../utils/platforms';
import { isWebUrl } from '@/lib/utils/isWebUrl';
import { workTitle, writtenWorks } from '../utils/works';
import { ArtistSection } from './ArtistSection';
import { PlatformLinkButton } from './PlatformLink';
import type { components } from '@/lib/api/generated/types';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

/** A writer's works, each linked to its article when there is one, and the places to read them. */
export function WritingsSection({ artist }: { artist: ArtistViewDTO }) {
  if (!hasWritingsSection(artist.categories)) return null;

  const works = writtenWorks(artist);
  const about = groupLinks(artist.socialLinks ?? []).about;
  const reading = READING_PLATFORMS.flatMap((platform) => about.filter((link) => link.platform === platform));
  if (works.length === 0 && reading.length === 0) return null;

  return (
    <ArtistSection title="Твори">
      {reading.length > 0 && (
        <ul className="mb-4 flex flex-wrap gap-2">
          {reading.map((link) => (
            <li key={link.url}>
              <PlatformLinkButton
                platform={link.platform}
                url={link.url}
                variant={link.platform === 'WIKISOURCE' ? 'default' : 'outline'}
              >
                {link.platform === 'WIKISOURCE' ? 'Читати на Вікіджерелах' : link.label}
              </PlatformLinkButton>
            </li>
          ))}
        </ul>
      )}
      {works.length > 0 && (
        <ul className="aero-groupbox divide-y divide-[#dbe7f5]">
          {works.map((work, i) => (
            <li
              key={work.wikidataId || work.articleUrl || i}
              className="flex min-h-[44px] items-center justify-between gap-4 px-4 py-1"
            >
              {isWebUrl(work.articleUrl) ? (
                <a
                  href={work.articleUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="aero-link inline-flex min-h-[44px] min-w-0 items-center break-words"
                >
                  {workTitle(work)}
                </a>
              ) : (
                <span className="min-w-0 break-words py-2">{workTitle(work)}</span>
              )}
              {work.year != null && (
                <span className="shrink-0 text-sm tabular-nums text-muted-foreground">{work.year}</span>
              )}
            </li>
          ))}
        </ul>
      )}
    </ArtistSection>
  );
}
