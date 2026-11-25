// An artist's notable works: which of them the gallery shows, which the list of writings, and how they are credited.
export const UNTITLED = 'Без назви';

export interface WorkLike {
  title?: string;
  imageUrl?: string;
  fullImageUrl?: string;
  imageCredit?: { author?: string; license?: string };
}

interface ArtistWithWorks<W extends WorkLike> {
  categories?: string[];
  works?: W[];
}

export const workTitle = (work: WorkLike): string => work.title?.trim() || UNTITLED;

export const hasImage = (work: WorkLike): boolean => !!(work.imageUrl || work.fullImageUrl);

/** «Автор: … · ліцензія · Wikimedia Commons», or null when the image has no credit. */
export function workCredit(work: WorkLike): string | null {
  const credit = work.imageCredit;
  if (!credit) return null;
  return [credit.author && `Автор: ${credit.author}`, credit.license, 'Wikimedia Commons'].filter(Boolean).join(' · ');
}

/** Works of a visual artist that have a picture. */
export function galleryWorks<W extends WorkLike>(artist: ArtistWithWorks<W>): W[] {
  if (!artist.categories?.includes('VISUAL_ARTIST')) return [];
  return (artist.works ?? []).filter(hasImage);
}

/** Works of a writer; when the writer is also a visual artist, the pictures stay in the gallery. */
export function writtenWorks<W extends WorkLike>(artist: ArtistWithWorks<W>): W[] {
  if (!artist.categories?.includes('WRITER')) return [];
  const works = artist.works ?? [];
  return artist.categories.includes('VISUAL_ARTIST') ? works.filter((work) => !hasImage(work)) : works;
}
