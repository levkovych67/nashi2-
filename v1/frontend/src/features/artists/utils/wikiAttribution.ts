import type { components } from '@/lib/api/generated/types';
import { isWebUrl } from '../../../lib/utils/isWebUrl.ts';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

export interface WikiAttribution {
  articleUrl: string;
  articleTitle: string;
  image?: { text: string; url?: string };
}

/**
 * What to credit for an artist imported from Wikipedia: the article (CC BY-SA 4.0) and the photo (its own license).
 * Returns null for artists added through the form.
 */
export function getWikiAttribution(
  artist: Pick<ArtistViewDTO, 'source' | 'wikiUrl' | 'imageCredit'> & Partial<ArtistViewDTO>,
): WikiAttribution | null {
  if (artist.source !== 'WIKIPEDIA' || !isWebUrl(artist.wikiUrl)) return null;

  const lastSegment = artist.wikiUrl.split('/').pop() ?? '';
  let articleTitle = lastSegment;
  try {
    articleTitle = decodeURIComponent(lastSegment);
  } catch {
    // keep the raw segment if the link is malformed
  }
  articleTitle = articleTitle.replace(/_/g, ' ');

  const attribution: WikiAttribution = { articleUrl: artist.wikiUrl, articleTitle };

  const credit = artist.imageCredit;
  if (credit) {
    const parts = [credit.author, credit.license].filter((part): part is string => Boolean(part));
    attribution.image = {
      text: `Фото: ${[...parts, 'Wikimedia Commons'].join(', ')}`,
      url: isWebUrl(credit.filePageUrl) ? credit.filePageUrl : undefined,
    };
  }
  return attribution;
}
