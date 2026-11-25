// Turns artist links into privacy-friendly embeds; anything we cannot embed safely returns null.
const YT_NOCOOKIE = 'https://www.youtube-nocookie.com/embed/';

function parse(url: string): URL | null {
  try {
    const u = new URL(url);
    return u.protocol === 'http:' || u.protocol === 'https:' ? u : null;
  } catch {
    return null;
  }
}

export function youtubeEmbed(url: string): string | null {
  const u = parse(url);
  if (!u) return null;
  const host = u.hostname.replace(/^www\.|^m\./, '');
  if (host === 'youtu.be') {
    const id = u.pathname.split('/')[1];
    return id && /^[\w-]+$/.test(id) ? YT_NOCOOKIE + id : null;
  }
  if (host !== 'youtube.com') return null;
  const channel = u.pathname.match(/^\/channel\/UC([\w-]+)\/?$/);
  if (channel) return `${YT_NOCOOKIE}videoseries?list=UU${channel[1]}`;
  if (u.pathname === '/watch') {
    const id = u.searchParams.get('v');
    return id && /^[\w-]+$/.test(id) ? YT_NOCOOKIE + id : null;
  }
  return null;
}

export function spotifyEmbed(url: string): string | null {
  const u = parse(url);
  if (!u || u.hostname !== 'open.spotify.com') return null;
  const m = u.pathname.match(/^\/(?:intl-[a-z]{2}(?:-[a-z]+)?\/)?(artist|album|track|playlist)\/([A-Za-z0-9]+)\/?$/);
  return m ? `https://open.spotify.com/embed/${m[1]}/${m[2]}` : null;
}

export function searchLinks(name: string): { youtube: string; spotify: string } {
  const q = encodeURIComponent(name);
  return {
    youtube: `https://www.youtube.com/results?search_query=${q}`,
    spotify: `https://open.spotify.com/search/${q}`,
  };
}
