import { linksForLinksSection, type LinkExclude, type LinkGroup } from '../utils/platforms';
import { ArtistSection } from './ArtistSection';
import { PlatformLinkButton } from './PlatformLink';
import type { components } from '@/lib/api/generated/types';

type SocialLinkDTO = components['schemas']['SocialLinkDTO'];

const GROUPS: { key: LinkGroup; title: string }[] = [
  { key: 'listen', title: 'Слухати' },
  { key: 'about', title: 'Про творчість' },
  { key: 'social', title: 'Соцмережі' },
  { key: 'other', title: 'Інше' },
];

/**
 * The links of the artist, grouped, each with its Ukrainian name and a pictogram. `exclude` leaves out the groups and
 * platforms a section above already shows («Слухати», «Твори»).
 */
export function ArtistLinks({ links, exclude }: { links?: SocialLinkDTO[]; exclude?: LinkExclude }) {
  const grouped = linksForLinksSection(links ?? [], exclude);
  const groups = GROUPS.filter((group) => grouped[group.key].length > 0);
  if (groups.length === 0) return null;

  return (
    <ArtistSection title="Посилання" className="mb-0">
      <div className="space-y-4">
        {groups.map((group) => (
          <div key={group.key} className="grid gap-2 sm:grid-cols-[9rem_1fr] sm:items-start sm:gap-4">
            <h3 className="text-base font-semibold sm:pt-3">{group.title}</h3>
            <ul className="flex flex-wrap gap-2">
              {grouped[group.key].map((link) => (
                <li key={link.url}>
                  <PlatformLinkButton platform={link.platform} url={link.url}>
                    {link.label}
                  </PlatformLinkButton>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </ArtistSection>
  );
}
