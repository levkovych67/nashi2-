import { useEffect, useRef, useState } from 'react';
import { Play } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { PlatformIcon } from './PlatformLink';

interface EmbedCardProps {
  /** PLATFORM_INFO key, for the icon */
  platform: 'YOUTUBE' | 'SPOTIFY';
  /** Name shown to the visitor */
  label: string;
  src: string;
  title: string;
  /** The only player of the section, across the whole row. */
  alone?: boolean;
}

/**
 * A player that loads only when asked: until the click there is no iframe, so the other site sets no cookies and
 * learns nothing about the visit.
 */
export function EmbedCard({ platform, label, src, title, alone = false }: EmbedCardProps) {
  const [active, setActive] = useState(false);
  const frameRef = useRef<HTMLIFrameElement>(null);

  // The button the visitor pressed is gone; keep the keyboard where the player now is.
  useEffect(() => {
    if (active) frameRef.current?.focus();
  }, [active]);

  // min-w-0: with aspect-ratio a min-height becomes a min-width and would push a grid column off a phone screen.
  // On wider screens two players stand at Spotify's full height, side by side; a YouTube player alone stays 16:9, so
  // the video fills it.
  const box = cn(
    'w-full min-w-0 rounded-[3px]',
    platform === 'SPOTIFY'
      ? 'h-[352px]'
      : cn('aspect-video min-h-[200px]', !alone && 'md:aspect-auto md:h-[352px]'),
  );

  if (active) {
    return (
      <iframe
        ref={frameRef}
        src={src}
        title={title}
        loading="lazy"
        allow="encrypted-media; picture-in-picture"
        allowFullScreen
        className={cn(box, 'block border-0 bg-[#0b1b2b]')}
      />
    );
  }

  return (
    <div className={cn(box, 'aero-groupbox flex flex-col items-center justify-center gap-3 p-5 text-center')}>
      <p className="flex items-center gap-2 text-lg font-semibold text-[color:var(--aero-instruction)]">
        <PlatformIcon platform={platform} className="h-5 w-5" />
        {label}
      </p>
      <Button type="button" onClick={() => setActive(true)}>
        <Play aria-hidden="true" />
        Увімкнути плеєр
      </Button>
      <p className="max-w-xs text-xs text-muted-foreground">
        Плеєр завантажиться з {label} і може встановити їхні cookies
      </p>
    </div>
  );
}
