import { Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { groupLinks, hasListenSection, type GroupedLink } from '../utils/platforms';
import { searchLinks, spotifyEmbed, youtubeEmbed } from '../utils/media';
import { ArtistSection } from './ArtistSection';
import { EmbedCard } from './EmbedCard';
import { PlatformLinkButton } from './PlatformLink';
import type { components } from '@/lib/api/generated/types';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

const firstEmbed = (links: GroupedLink[], platform: string, toEmbed: (url: string) => string | null) =>
  links
    .filter((link) => link.platform === platform)
    .map((link) => toEmbed(link.url))
    .find((src): src is string => !!src) ?? null;

/** Players for musicians and bands, the buttons of every music platform, or a search when there is none. */
export function ListenSection({ artist }: { artist: ArtistViewDTO }) {
  if (!hasListenSection(artist.categories)) return null;

  const name = artist.name?.trim() ?? '';
  const links = groupLinks(artist.socialLinks ?? []).listen;
  const youtube = firstEmbed(links, 'YOUTUBE', youtubeEmbed);
  const spotify = firstEmbed(links, 'SPOTIFY', spotifyEmbed);
  const hasYoutubeOrSpotify = links.some((link) => link.platform === 'YOUTUBE' || link.platform === 'SPOTIFY');
  const search = !hasYoutubeOrSpotify && name ? searchLinks(name) : null;
  if (links.length === 0 && !search) return null;

  return (
    <ArtistSection title="Слухати">
      <div className="space-y-4">
        {(youtube || spotify) && (
          // One player takes the whole row; two stand side by side from md.
          <div className={cn('grid gap-4', youtube && spotify && 'md:grid-cols-2')}>
            {youtube && (
              <EmbedCard
                platform="YOUTUBE"
                label="YouTube"
                src={youtube}
                title={`Плеєр YouTube: ${name}`}
                alone={!spotify}
              />
            )}
            {spotify && <EmbedCard platform="SPOTIFY" label="Spotify" src={spotify} title={`Плеєр Spotify: ${name}`} />}
          </div>
        )}
        <ul className="flex flex-wrap gap-2">
          {links.map((link) => (
            <li key={link.url}>
              <PlatformLinkButton platform={link.platform} url={link.url}>
                {link.label}
              </PlatformLinkButton>
            </li>
          ))}
          {search && (
            <>
              <li>
                <SearchButton url={search.youtube}>Пошук на YouTube</SearchButton>
              </li>
              <li>
                <SearchButton url={search.spotify}>Пошук на Spotify</SearchButton>
              </li>
            </>
          )}
        </ul>
      </div>
    </ArtistSection>
  );
}

function SearchButton({ url, children }: { url: string; children: string }) {
  return (
    <Button variant="outline" size="sm" asChild>
      <a href={url} target="_blank" rel="noopener noreferrer">
        <Search aria-hidden="true" />
        {children}
      </a>
    </Button>
  );
}
