import { createHash } from 'node:crypto';
export const hash = value => createHash('sha256').update(value).digest();
export const fixtureId = qid => hash('fixture:' + qid).toString('hex').slice(0, 24);
export function pinFor(id, place, precision) {
  if (precision === 'EXACT') return {lat:place.lat, lon:place.lon};
  const radius = Number.isFinite(place.areaKm2) && place.areaKm2 > 0
    ? Math.max(150, Math.min(8000, 600 * Math.sqrt(place.areaKm2 / Math.PI)))
    : {CITY:3000,TOWN:1200,VILLAGE:400,OTHER:400}[place.type];
  const bytes = hash(id + ':' + place.placeId);
  const r = radius * Math.sqrt(bytes.readUInt32BE(0) / 2**32);
  const theta = 2 * Math.PI * bytes.readUInt32BE(4) / 2**32;
  return {
    lat:place.lat + r * Math.cos(theta) / 111320,
    lon:place.lon + r * Math.sin(theta) / (111320 * Math.cos(place.lat * Math.PI / 180))
  };
}
