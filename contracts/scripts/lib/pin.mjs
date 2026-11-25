// Reference implementation of the pin placement formula (docs/02-contracts.md section 4).
// Mirrors backend PinPlacer.java; contracts/test-vectors/pins.json is generated from it and the
// backend and this module must agree on every vector.
import { createHash } from 'node:crypto';

const TWO_POW_32 = 4294967296;
const CITY_RADIUS_M = { CITY: 3000, TOWN: 1200, VILLAGE: 400, OTHER: 400 };

const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

/** SHA-256 digest of a UTF-8 string (Buffer). */
export const hash = (value) => createHash('sha256').update(value, 'utf8').digest();

/** Stable 24-hex id for a fixture record, derived from its Wikidata QID (studio fixtures only). */
export const fixtureId = (qid) => hash('fixture:' + qid).toString('hex').slice(0, 24);

/** Radius in metres: from the area when known, otherwise from the settlement type. */
export function radiusMeters(type, areaKm2) {
  if (areaKm2 != null) return clamp(0.6 * Math.sqrt(areaKm2 / Math.PI) * 1000, 150, 8000);
  return CITY_RADIUS_M[type];
}

/**
 * Deterministic pin for an artist inside a place: { lat, lon }.
 * place = { placeId, lat, lon, type, areaKm2 }. EXACT returns the place's own coordinates.
 */
export function pinFor(artistId, place, precision) {
  if (precision === 'EXACT') return { lat: place.lat, lon: place.lon };
  const R = radiusMeters(place.type, place.areaKm2);
  const h = hash(`${artistId}:${place.placeId}`);
  const u1 = h.readUInt32BE(0) / TWO_POW_32;
  const u2 = h.readUInt32BE(4) / TWO_POW_32;
  const r = R * Math.sqrt(u1);
  const theta = 2 * Math.PI * u2;
  const lat = place.lat + (r * Math.cos(theta)) / 111320.0;
  const lon = place.lon + (r * Math.sin(theta)) / (111320.0 * Math.cos((place.lat * Math.PI) / 180));
  return { lat, lon };
}
