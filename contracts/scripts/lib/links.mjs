const groups = {
  SPOTIFY:'MUSIC',APPLE_MUSIC:'MUSIC',DEEZER:'MUSIC',YOUTUBE_MUSIC:'MUSIC',SOUNDCLOUD:'MUSIC',BANDCAMP:'MUSIC',
  YOUTUBE:'VIDEO',WIKIPEDIA:'REFERENCE',WEBSITE:'REFERENCE',INSTAGRAM:'SOCIAL',FACEBOOK:'SOCIAL',TIKTOK:'SOCIAL',
  TELEGRAM:'SOCIAL',X:'SOCIAL',OTHER:'SOCIAL'
};
const alias = {WIKI:'WIKIPEDIA',DISCOGS:'OTHER',LASTFM:'OTHER',ESU:'OTHER'};
export function safeUrl(value) {
  if (typeof value !== 'string' || !URL.canParse(value)) return null;
  return ['http:', 'https:'].includes(new URL(value).protocol) ? value : null;
}
export function mapLinks(links = []) {
  return links.flatMap(link => {
    const platform = alias[link.platform] ?? link.platform;
    const group = groups[platform];
    return group && safeUrl(link.url) ? [{platform,group,url:new URL(link.url).href}] : [];
  });
}
