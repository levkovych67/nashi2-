// Writes contracts/test-vectors/pins.json from the reference formula in lib/pin.mjs.
// Run from the repo root: node contracts/scripts/pin-vectors.mjs
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { pinFor } from './lib/pin.mjs';

const OUT = fileURLToPath(new URL('../test-vectors/pins.json', import.meta.url));

const KYIV = { placeId: 'Q1899', lat: 50.45, lon: 30.5236, type: 'CITY', areaKm2: 839 };
const BRODY = { placeId: 'Q465104', lat: 50.078055555, lon: 25.154166666, type: 'TOWN', areaKm2: null };
const VILLAGE = { placeId: 'Q4220129', lat: 47.255555555, lon: 31.363611111, type: 'VILLAGE', areaKm2: 2.1 };
const OTHER = { placeId: 'Q9000001', lat: 49.1, lon: 28.2, type: 'OTHER', areaKm2: null };
const SMALL = { placeId: 'Q9000002', lat: 48.5, lon: 35.0, type: 'VILLAGE', areaKm2: 0.01 };

const cases = [
  ['Київ, артист 1', '65a1b2c3d4e5f60718293a41', KYIV, 'SETTLEMENT'],
  ['Київ, артист 2', '65a1b2c3d4e5f60718293a42', KYIV, 'SETTLEMENT'],
  ['Київ, артист 3', '65a1b2c3d4e5f60718293a43', KYIV, 'SETTLEMENT'],
  ['Броди (TOWN, без площі)', '65a1b2c3d4e5f60718293a44', BRODY, 'SETTLEMENT'],
  ['Село (VILLAGE, 2.1 км²)', '65a1b2c3d4e5f60718293a45', VILLAGE, 'SETTLEMENT'],
  ['OTHER без площі', '65a1b2c3d4e5f60718293a46', OTHER, 'SETTLEMENT'],
  ['EXACT', '65a1b2c3d4e5f60718293a47', KYIV, 'EXACT'],
  ['Мала площа (0.01 км² → 150 м)', '65a1b2c3d4e5f60718293a48', SMALL, 'SETTLEMENT'],
];

const vectors = cases.map(([name, artistId, place, precision]) => ({
  name,
  artistId,
  place,
  precision,
  expected: pinFor(artistId, place, precision),
}));

writeFileSync(OUT, JSON.stringify(vectors, null, 2) + '\n');
console.log(`wrote ${vectors.length} vectors to ${OUT}`);
