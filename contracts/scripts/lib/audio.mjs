import { hash } from './pin.mjs';

export function clipsFor(id) {
  const bytes = hash(id);
  return Array.from({length:1 + bytes[0] % 3}, (_,i) => {
    const number = 1 + (bytes[1] + i) % 6;
    return {
      clipId:'c_' + id + '_' + (i+1),
      title:'Демоетюд ' + number,
      source:'UPLOAD',
      durationSec:15,
      externalUrl:null,
      url:'/fixtures/audio/clip-0' + number + '.wav'
    };
  });
}
export function fragmentFor(clip) {
  return {
    ...clip, startSec:0, fadeInMs:300, fadeOutMs:600,
    attribution:'Синтетичний демонстраційний звук студії «Наші»',
    expiresAt:null
  };
}
