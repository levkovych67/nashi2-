// Artists used by the studio previews. Selections survive V1 ordering changes.
const showcase = new Set([
  'okean-elzy', 'odyn-v-kanoe', 'plach-yeremii', 'taras-chubai', 'ruslana-lyzhychko',
  'oksana-lyniv', 'solomiia-krushelnytska', 'dakhabrakha', 'zhadan-i-sobaky',
  'oksana-herasymenko', 'andrii-kuzmenko', 'sviatoslav-vakarchuk'
]);
const quotas = [
  [10, a => a.kind === 'BAND'], [25, a => a.kind === 'PERSON'],
  [15, a => a.images.length > 0], [8, a => !a.images.length],
  [12, a => a.hasAudio], [12, a => !a.hasAudio],
  [5, a => Boolean(a.youtubeChannelUrl)]
];
export function pickDetailSamples(artists, indexItems) {
  const selected = new Set();
  const counts = quotas.map(() => 0);
  for (const artist of artists) {
    if (!artist.textSource) continue;
    const matches = quotas.map(([, match]) => match(artist));
    if (selected.size < 40 || matches.some((yes,i) => yes && counts[i] < quotas[i][0])) {
      selected.add(artist.slug);
      matches.forEach((yes,i) => { if (yes) counts[i]++; });
    }
  }
  for (const artist of artists) {
    if (!artist.textSource || showcase.has(artist.slug)) selected.add(artist.slug);
  }
  for (const item of indexItems) selected.add(item.slug);
  return artists.filter(artist => selected.has(artist.slug));
}
