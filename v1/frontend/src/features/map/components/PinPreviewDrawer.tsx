import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { OptimizedImage } from '@/components/ui/optimized-image';
import type { components } from '@/lib/api/generated/types';
import { pinKindLabels, pinYearLabel } from '../utils/pinLabels';

type MapPinDTO = components['schemas']['MapPinDTO'];

interface PinPreviewDrawerProps {
  pin: MapPinDTO | null;
  isOpen: boolean;
  onClose: () => void;
}

export function PinPreviewDrawer({ pin, isOpen, onClose }: PinPreviewDrawerProps) {
  if (!pin) return null;

  const isArtist = pin.type === 'ARTIST';
  const place = isArtist ? [pin.city, pinYearLabel(pin)].filter(Boolean).join(' · ') : '';

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] md:bottom-6 md:left-auto md:right-6 md:max-h-[80vh] md:w-[420px] md:rounded-lg"
      >
        <SheetHeader>
          <SheetTitle className="pr-6 text-2xl md:text-[26px]">{pin.name}</SheetTitle>
          <SheetDescription className="sr-only">
            {isArtist ? 'Інформація про артиста' : 'Інформація про подію'}
          </SheetDescription>
        </SheetHeader>

        <div className="space-y-4">
          {/* Avatar/Image */}
          {pin.avatarUrl && (
            <OptimizedImage
              src={pin.avatarUrl}
              alt={pin.name || ''}
              containerClassName="w-full h-48 rounded-md border border-[#9cc6ec] shadow-elegant"
            />
          )}

          {/* Info: an artist's kinds and genres, or an event's tag and venue */}
          {isArtist ? (
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                {pinKindLabels(pin).map((label) => (
                  <span key={label} className="aero-tag font-semibold">
                    {label}
                  </span>
                ))}
                {pin.genres?.slice(0, 3).map((genre) => (
                  <span key={genre} className="aero-tag">
                    {genre}
                  </span>
                ))}
              </div>
              {place && (
                <p className="flex items-center gap-1.5 text-sm text-[var(--aero-text-2)]">
                  <MapPin className="h-4 w-4 flex-none" aria-hidden="true" />
                  {place}
                </p>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-2">
              <span className="aero-tag">Подія</span>
              {pin.style && <span className="aero-tag">{pin.style}</span>}
            </div>
          )}

          {/* Audio preview */}
          {pin.audioUrl && (
            <div>
              <p className="text-sm text-muted-foreground mb-2">Слухати:</p>
              <audio controls className="w-full rounded-full">
                <source src={pin.audioUrl} type="audio/mpeg" />
                Ваш браузер не підтримує аудіо елемент.
              </audio>
            </div>
          )}

          {/* Actions: a real link, so it opens in a new tab and shows its address */}
          {isArtist && pin.slug && (
            <Button asChild className="w-full">
              <Link to={`/artists/${encodeURIComponent(pin.slug)}`}>Переглянути профіль</Link>
            </Button>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
