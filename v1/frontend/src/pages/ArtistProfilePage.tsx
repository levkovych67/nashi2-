import { useMemo, useState, type ReactNode } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Users } from 'lucide-react';
import { useArtistBySlug } from '@/lib/api/hooks/useArtists';
import { ApiError } from '@/lib/api/errors';
import type { components } from '@/lib/api/generated/types';
import { Button } from '@/components/ui/button';
import { OptimizedImage } from '@/components/ui/optimized-image';
import { SEO } from '@/components/SEO';
import { cn } from '@/lib/utils';
import { WikiAttribution } from '@/features/artists/components/WikiAttribution';
import { ArtistHero, ArtistHeroPhoto } from '@/features/artists/components/ArtistHero';
import { ArtistFacts } from '@/features/artists/components/ArtistFacts';
import { ListenSection } from '@/features/artists/components/ListenSection';
import { WorksGallery } from '@/features/artists/components/WorksGallery';
import { WritingsSection } from '@/features/artists/components/WritingsSection';
import { ArtistLinks } from '@/features/artists/components/ArtistLinks';
import { getWikiAttribution } from '@/features/artists/utils/wikiAttribution';
import { hasListenSection, hasWritingsSection, linksSectionExclude } from '@/features/artists/utils/platforms';
import { artistJsonLd, seoDescription } from '@/features/artists/utils/artistSeo';
import { CATEGORY_LABEL } from '@/features/artists/utils/lifeDates';
import { galleryWorks } from '@/features/artists/utils/works';

type ArtistViewDTO = components['schemas']['ArtistViewDTO'];

export function ArtistProfilePage() {
  const { slug = '' } = useParams<{ slug: string }>();
  const { data: artist, isLoading, error, refetch, isFetching } = useArtistBySlug(slug);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="aero-glass px-8 py-6 text-center" role="status">
          <div className="aero-spinner mx-auto mb-4" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">Завантаження...</p>
        </div>
      </div>
    );
  }

  const notFound = error instanceof ApiError && error.status === 404;
  if (error && !notFound) {
    return (
      <StateCard title="Не вдалося завантажити сторінку">
        <Button type="button" onClick={() => refetch()} disabled={isFetching}>
          Спробувати ще
        </Button>
      </StateCard>
    );
  }

  if (!artist) {
    return (
      <StateCard title="Митця не знайдено">
        <Button asChild>
          <Link to="/artists">До списку митців</Link>
        </Button>
      </StateCard>
    );
  }

  return <ArtistProfile key={artist.slug} artist={artist} />;
}

function StateCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="container mx-auto px-4 py-20">
      <SEO title={title} />
      <div className="aero-glass mx-auto max-w-md px-8 py-8 text-center">
        <h1 className="text-2xl font-heading mb-4">{title}</h1>
        {children}
      </div>
    </div>
  );
}

function ArtistProfile({ artist }: { artist: ArtistViewDTO }) {
  const [selectedImage, setSelectedImage] = useState(0);
  // Photos that failed to load: the page then has no photo band and the card shows the initials.
  const [brokenImages, setBrokenImages] = useState<string[]>([]);
  const structuredData = useMemo(() => artistJsonLd(artist), [artist]);

  const images = artist.imageUrls || [];
  const currentImage = images[selectedImage];
  const photo = currentImage && !brokenImages.includes(currentImage) ? currentImage : undefined;
  const wikiAttribution = getWikiAttribution(artist);
  const kinds = (artist.categories ?? []).map((c) => CATEGORY_LABEL[c]?.toLowerCase()).filter(Boolean);
  // «Слухати», «Твори» and «Роботи» show their links already; «Посилання» lists only the rest.
  const linksExclude = linksSectionExclude({
    hasListen: hasListenSection(artist.categories),
    hasWritings: hasWritingsSection(artist.categories),
    hasGallery: galleryWorks(artist).length > 0,
  });

  return (
    <>
      <SEO
        title={artist.name || 'Митець'}
        description={seoDescription(artist) || `Профіль митця ${artist.name} на платформі НАШІ`}
        keywords={[artist.name, ...kinds, ...(artist.genres ?? []), 'українська культура'].filter(Boolean).join(', ')}
        ogImage={images[0]}
        structuredData={structuredData}
      />
      <div className="min-h-screen">
        {photo && (
          <ArtistHeroPhoto
            key={photo}
            src={photo}
            alt={artist.name || ''}
            onError={() => setBrokenImages((broken) => [...broken, photo])}
          />
        )}

        {/* Without a photo nothing sits under the card, so it must not slide up under the header. */}
        <div className={cn('container mx-auto px-4 pb-12 relative z-10', photo ? '-mt-20' : 'pt-6 md:pt-10')}>
          <article className="aero-glass p-6 md:p-8">
            <ArtistHero artist={artist} hasPhoto={!!photo} />

            {/* Gallery Thumbnails */}
            {images.length > 1 && (
              <div className="flex gap-2 overflow-x-auto mb-8 pb-2">
                {images.map((img, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setSelectedImage(idx)}
                    aria-label={`Фото ${idx + 1}`}
                    aria-pressed={idx === selectedImage}
                    className={`flex-shrink-0 w-20 h-20 rounded-soft overflow-hidden border-2 transition-all ${
                      idx === selectedImage ? 'border-accent' : 'border-transparent opacity-60'
                    }`}
                  >
                    <OptimizedImage src={img} alt="" containerClassName="w-full h-full" />
                  </button>
                ))}
              </div>
            )}

            <ArtistFacts artist={artist} />

            {/* Biography */}
            {artist.biography && (
              <div className="mb-8">
                <h2 className="text-2xl font-heading font-semibold mb-4">Біографія</h2>
                <p className="text-foreground/90 whitespace-pre-wrap leading-relaxed">{artist.biography}</p>
              </div>
            )}

            {/* Imported from Wikipedia: credit the article and the photo */}
            {wikiAttribution && <WikiAttribution attribution={wikiAttribution} />}

            <ListenSection artist={artist} />
            <WorksGallery artist={artist} />
            <WritingsSection artist={artist} />

            {/* Press Release */}
            {artist.pressRelease && (
              <div className="mb-8">
                <h2 className="text-2xl font-heading font-semibold mb-4">Прес-реліз</h2>
                <p className="text-foreground/90 whitespace-pre-wrap leading-relaxed">{artist.pressRelease}</p>
              </div>
            )}

            {/* Demo Tracks */}
            {artist.demoTrackUrls && artist.demoTrackUrls.length > 0 && (
              <div className="mb-8">
                <h2 className="text-2xl font-heading font-semibold mb-4">Демо-треки</h2>
                <div className="space-y-3">
                  {artist.demoTrackUrls.map((url, idx) => (
                    <audio key={idx} controls className="w-full">
                      <source src={url} type="audio/mpeg" />
                      Ваш браузер не підтримує аудіо елемент.
                    </audio>
                  ))}
                </div>
              </div>
            )}

            {/* Members */}
            {artist.members && artist.members.length > 0 && (
              <div className="mb-8">
                <h2 className="text-2xl font-heading font-semibold mb-4 flex items-center gap-2">
                  <Users className="w-6 h-6" />
                  Учасники
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {artist.members.map((member, idx) => (
                    <div key={idx} className="aero-groupbox p-4">
                      <p className="font-medium">
                        {member.firstName} {member.lastName}
                      </p>
                      {member.role && <p className="text-sm text-muted-foreground">{member.role}</p>}
                      {member.city && <p className="text-xs text-muted-foreground/70 mt-1">{member.city}</p>}
                    </div>
                  ))}
                </div>
              </div>
            )}

            <ArtistLinks links={artist.socialLinks} exclude={linksExclude} />
          </article>
        </div>
      </div>
    </>
  );
}
