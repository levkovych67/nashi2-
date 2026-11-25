import { test } from 'node:test';
import assert from 'node:assert/strict';
import { youtubeEmbed, spotifyEmbed, searchLinks } from '../src/features/artists/utils/media.ts';

test('youtubeEmbed', () => {
  assert.equal(youtubeEmbed('https://www.youtube.com/channel/UCabc123'), 'https://www.youtube-nocookie.com/embed/videoseries?list=UUabc123');
  assert.equal(youtubeEmbed('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
  assert.equal(youtubeEmbed('https://youtu.be/dQw4w9WgXcQ'), 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
  assert.equal(youtubeEmbed('https://www.youtube.com/@handle'), null);
  assert.equal(youtubeEmbed('nonsense'), null);
});

test('spotifyEmbed', () => {
  assert.equal(spotifyEmbed('https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb'), 'https://open.spotify.com/embed/artist/4Z8W4fKeB5YxbusRsdQVPb');
  assert.equal(spotifyEmbed('https://open.spotify.com/intl-uk/album/abc123'), 'https://open.spotify.com/embed/album/abc123');
  assert.equal(spotifyEmbed('https://open.spotify.com/user/abc'), null);
});

test('searchLinks', () => {
  assert.deepEqual(searchLinks('Скрябін & Ко'), {
    youtube: 'https://www.youtube.com/results?search_query=' + encodeURIComponent('Скрябін & Ко'),
    spotify: 'https://open.spotify.com/search/' + encodeURIComponent('Скрябін & Ко'),
  });
});
