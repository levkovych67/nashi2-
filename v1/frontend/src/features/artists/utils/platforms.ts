// Groups an artist's links by platform so the page can show "listen", "about" and "social" sections.
export type LinkGroup = 'listen' | 'about' | 'social' | 'other';

export const PLATFORM_INFO: Record<string, { label: string; group: LinkGroup }> = {
  YOUTUBE: { label: 'YouTube', group: 'listen' },
  SPOTIFY: { label: 'Spotify', group: 'listen' },
  APPLE_MUSIC: { label: 'Apple Music', group: 'listen' },
  DEEZER: { label: 'Deezer', group: 'listen' },
  SOUNDCLOUD: { label: 'SoundCloud', group: 'listen' },
  BANDCAMP: { label: 'Bandcamp', group: 'listen' },
  DISCOGS: { label: 'Discogs', group: 'about' },
  MUSICBRAINZ: { label: 'MusicBrainz', group: 'about' },
  LASTFM: { label: 'Last.fm', group: 'about' },
  COMMONS: { label: 'Wikimedia Commons', group: 'about' },
  WIKISOURCE: { label: 'Вікіджерела', group: 'about' },
  GOODREADS: { label: 'Goodreads', group: 'about' },
  OPEN_LIBRARY: { label: 'Open Library', group: 'about' },
  ARTSY: { label: 'Artsy', group: 'about' },
  ESU: { label: 'Енциклопедія сучасної України', group: 'about' },
  CHTYVO: { label: 'Чтиво', group: 'about' },
  WIKIART: { label: 'WikiArt', group: 'about' },
  INSTAGRAM: { label: 'Instagram', group: 'social' },
  FACEBOOK: { label: 'Facebook', group: 'social' },
  TIKTOK: { label: 'TikTok', group: 'social' },
  TELEGRAM: { label: 'Telegram', group: 'social' },
  X: { label: 'X', group: 'social' },
  WEBSITE: { label: 'Офіційний сайт', group: 'other' },
  WIKI: { label: 'Вікіпедія', group: 'other' },
  WIKIDATA: { label: 'Вікідані', group: 'other' },
  OTHER: { label: 'Посилання', group: 'other' },
};

export interface GroupedLink {
  platform: string;
  label: string;
  url: string;
}

export function groupLinks(
  links: { platform?: string; url?: string }[],
): Record<LinkGroup, GroupedLink[]> {
  const result: Record<LinkGroup, GroupedLink[]> = { listen: [], about: [], social: [], other: [] };
  const seen = new Set<string>();
  for (const link of links) {
    if (!link.url) continue;
    let parsed: URL;
    try {
      parsed = new URL(link.url);
    } catch {
      continue;
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') continue;
    if (seen.has(link.url)) continue;
    seen.add(link.url);
    const platform = link.platform ?? 'OTHER';
    // hasOwn: a platform named «constructor» or «toString» is an unknown platform, not an Object.prototype member.
    const info = Object.hasOwn(PLATFORM_INFO, platform) ? PLATFORM_INFO[platform] : undefined;
    if (info) result[info.group].push({ platform, label: info.label, url: link.url });
    else result.other.push({ platform, label: parsed.hostname, url: link.url });
  }
  return result;
}

/** Where the texts can be read or looked up, Wikisource first; «Твори» shows these links. */
export const READING_PLATFORMS = ['WIKISOURCE', 'CHTYVO', 'GOODREADS', 'OPEN_LIBRARY'];

/** Musicians and bands get «Слухати», with every listen link of the artist. */
export const hasListenSection = (categories?: string[]): boolean =>
  !!categories?.some((category) => category === 'MUSICIAN' || category === 'BAND');

/** Writers get «Твори», with every reading link of the artist. */
export const hasWritingsSection = (categories?: string[]): boolean => !!categories?.includes('WRITER');

export interface LinkExclude {
  groups?: LinkGroup[];
  platforms?: string[];
}

/**
 * What «Посилання» leaves out because a section above already shows it: «Слухати» every listen link, «Твори» every
 * reading link, «Роботи» (the gallery) the Commons link. «Слухати» and «Твори» stay away only when the artist has no
 * such links, so leaving the links out then changes nothing.
 */
export function linksSectionExclude({
  hasListen,
  hasWritings,
  hasGallery,
}: {
  hasListen: boolean;
  hasWritings: boolean;
  hasGallery: boolean;
}): LinkExclude {
  return {
    groups: hasListen ? ['listen'] : [],
    platforms: [...(hasWritings ? READING_PLATFORMS : []), ...(hasGallery ? ['COMMONS'] : [])],
  };
}

/** The links of «Посилання»: grouped like groupLinks, without the excluded groups and platforms. */
export function linksForLinksSection(
  links: { platform?: string; url?: string }[],
  exclude: LinkExclude = {},
): Record<LinkGroup, GroupedLink[]> {
  const grouped = groupLinks(links);
  const groups = exclude.groups ?? [];
  const platforms = exclude.platforms ?? [];
  for (const group of Object.keys(grouped) as LinkGroup[]) {
    grouped[group] = groups.includes(group) ? [] : grouped[group].filter((link) => !platforms.includes(link.platform));
  }
  return grouped;
}
