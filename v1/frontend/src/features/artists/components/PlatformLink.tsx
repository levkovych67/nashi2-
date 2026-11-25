import { createElement, type ReactNode } from 'react';
import {
  AtSign,
  AudioLines,
  BookMarked,
  BookOpen,
  BookText,
  Camera,
  Clapperboard,
  Database,
  Disc3,
  Globe,
  Headphones,
  Images,
  Landmark,
  Library,
  Link2,
  MonitorPlay,
  Music,
  Music2,
  Newspaper,
  Palette,
  Radio,
  Send,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { Button } from '@/components/ui/button';

// Plain pictograms instead of brand logos: the interface speaks only the language of forms.
const ICONS: Record<string, LucideIcon> = {
  YOUTUBE: MonitorPlay,
  SPOTIFY: Music,
  APPLE_MUSIC: Music2,
  DEEZER: Headphones,
  SOUNDCLOUD: AudioLines,
  BANDCAMP: Disc3,
  DISCOGS: Disc3,
  MUSICBRAINZ: Database,
  LASTFM: Radio,
  COMMONS: Images,
  WIKISOURCE: BookOpen,
  GOODREADS: BookMarked,
  OPEN_LIBRARY: Library,
  ARTSY: Palette,
  ESU: Landmark,
  CHTYVO: BookText,
  WIKIART: Palette,
  INSTAGRAM: Camera,
  FACEBOOK: Users,
  TIKTOK: Clapperboard,
  TELEGRAM: Send,
  X: AtSign,
  WEBSITE: Globe,
  WIKI: Newspaper,
  WIKIDATA: Database,
  OTHER: Link2,
};

export function PlatformIcon({ platform, className }: { platform: string; className?: string }) {
  const icon = Object.hasOwn(ICONS, platform) ? ICONS[platform] : Link2;
  return createElement(icon, { className, 'aria-hidden': true });
}

/** A command button that opens an artist's page on another site in a new tab. */
export function PlatformLinkButton({
  platform,
  url,
  children,
  variant = 'outline',
}: {
  platform: string;
  url: string;
  children: ReactNode;
  variant?: 'outline' | 'default';
}) {
  return (
    <Button variant={variant} size="sm" asChild>
      <a href={url} target="_blank" rel="noopener noreferrer">
        <PlatformIcon platform={platform} />
        {children}
      </a>
    </Button>
  );
}
