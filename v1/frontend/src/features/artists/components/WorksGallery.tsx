import { useRef, useState } from 'react';
import { ExternalLink, ImageOff, Images } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { isWebUrl } from '@/lib/utils/isWebUrl';
import { galleryWorks, workCredit, workTitle } from '../utils/works';
import { ArtistSection } from './ArtistSection';
import type { components } from '@/lib/api/generated/types';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];
type ArtistWork = components['schemas']['ArtistWork'];

/** The preview, then the full image if the preview fails, then an empty frame. */
function WorkThumb({ work }: { work: ArtistWork }) {
  const sources = [work.imageUrl, work.fullImageUrl].filter(
    (src, i, all): src is string => !!src && all.indexOf(src) === i,
  );
  const [attempt, setAttempt] = useState(0);
  const src = sources[attempt];

  if (!src) {
    return (
      <span className="flex aspect-square w-full items-center justify-center bg-[#e4eef9] text-muted-foreground">
        <ImageOff className="h-8 w-8" aria-hidden="true" />
      </span>
    );
  }
  return (
    <img
      src={src}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setAttempt((n) => n + 1)}
      className="aspect-square w-full bg-[#e4eef9] object-cover transition-transform duration-300 group-hover:scale-[1.03]"
    />
  );
}

/** Paintings and other visual works: a grid of previews that open a full view with the author and license. */
export function WorksGallery({ artist }: { artist: ArtistViewDTO }) {
  const [open, setOpen] = useState(false);
  const [index, setIndex] = useState(0);
  // The view has no Radix trigger, so it hands the keyboard back to the tile that opened it.
  const openerRef = useRef<HTMLButtonElement | null>(null);

  const works = galleryWorks(artist);
  if (works.length === 0) return null;

  const commons = (artist.socialLinks ?? []).find((link) => link.platform === 'COMMONS' && isWebUrl(link.url));
  const current = works[Math.min(index, works.length - 1)];
  const credit = workCredit(current);
  // Links from the API go into href only as http(s) addresses.
  const filePageUrl = current.imageCredit?.filePageUrl;
  const filePage = isWebUrl(filePageUrl) ? filePageUrl : undefined;
  const articleUrl = isWebUrl(current.articleUrl) ? current.articleUrl : undefined;
  const described = credit !== null || current.year != null;

  return (
    <ArtistSection title="Роботи">
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {works.map((work, i) => (
          <li key={work.wikidataId || work.fullImageUrl || work.imageUrl || i}>
            <button
              type="button"
              onClick={(event) => {
                openerRef.current = event.currentTarget;
                setIndex(i);
                setOpen(true);
              }}
              className="aero-groupbox aero-lift group block w-full overflow-hidden text-left"
            >
              <span className="block overflow-hidden">
                <WorkThumb work={work} />
              </span>
              <span className="block px-2.5 py-2">
                <span className="line-clamp-2 text-sm font-medium">{workTitle(work)}</span>
                {work.year != null && <span className="block text-xs text-muted-foreground">{work.year}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>

      {commons && (
        <a
          href={commons.url}
          target="_blank"
          rel="noopener noreferrer"
          className="aero-link mt-3 inline-flex min-h-[44px] items-center gap-1.5 text-sm"
        >
          <Images className="h-4 w-4" aria-hidden="true" />
          Більше робіт на Wikimedia Commons
        </a>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        {/* Without a year or a credit there is nothing to describe the view with; say so, or Radix warns. */}
        <DialogContent
          className="max-w-4xl"
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            openerRef.current?.focus();
          }}
          {...(described ? {} : { 'aria-describedby': undefined })}
        >
          <img
            key={current.fullImageUrl || current.imageUrl}
            src={current.fullImageUrl || current.imageUrl}
            alt={workTitle(current)}
            decoding="async"
            className="mx-auto max-h-[65dvh] w-auto max-w-full object-contain"
          />
          <div className="space-y-1">
            <DialogTitle>{workTitle(current)}</DialogTitle>
            {described && (
              <DialogDescription asChild>
                <div className="space-y-1">
                  {current.year != null && <p>{current.year}</p>}
                  {credit && (
                    <p>
                      {filePage ? (
                        <a href={filePage} target="_blank" rel="noopener noreferrer" className="aero-link">
                          {credit}
                        </a>
                      ) : (
                        credit
                      )}
                    </p>
                  )}
                </div>
              </DialogDescription>
            )}
            {articleUrl && (
              <a
                href={articleUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="aero-link inline-flex min-h-[44px] items-center gap-1.5 text-sm"
              >
                <ExternalLink className="h-4 w-4" aria-hidden="true" />
                Стаття про роботу
              </a>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </ArtistSection>
  );
}
