import mapshaper from 'mapshaper';
import polylabel from 'polylabel';
import { REGIONS } from './regions.mjs';

const knownIso = new Set(REGIONS.map((region) => region.iso));

function sourceInContractOrder(source) {
  if (source?.type !== 'FeatureCollection' || !Array.isArray(source.features)) {
    throw new Error('Expected a geoBoundaries FeatureCollection');
  }
  const byIso = new Map();
  const issues = [];
  for (const { properties, geometry } of source.features) {
    const iso = properties?.shapeISO;
    const name = properties?.shapeName ?? '(unknown name)';
    if (!knownIso.has(iso)) issues.push(`Missing or unknown shapeISO for ${name}: ${iso ?? '(empty)'}`);
    if (byIso.has(iso)) issues.push(`Duplicate shapeISO for ${name}: ${iso}`);
    if (!['Polygon', 'MultiPolygon'].includes(geometry?.type)) issues.push(`Expected a polygon for ${name}`);
    byIso.set(iso, geometry);
  }
  for (const iso of knownIso) if (!byIso.has(iso)) issues.push(`Missing region ${iso}`);
  if (issues.length) throw new Error(issues.join('\n'));
  return {
    type: 'FeatureCollection',
    features: REGIONS.map(({ code, name, iso }) => ({
      type: 'Feature', properties: { code, name }, geometry: byIso.get(iso),
    })),
  };
}

function ringArea(ring) {
  let twiceArea = 0;
  for (let i = 1; i < ring.length; i++) {
    const [ax, ay] = ring[i - 1];
    const [bx, by] = ring[i];
    twiceArea += ax * by - bx * ay;
  }
  return Math.abs(twiceArea) / 2;
}

function regionMeta(feature) {
  const { geometry, properties: { code, name } } = feature;
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
  const bbox = [Infinity, Infinity, -Infinity, -Infinity];
  let largest = null;
  let largestArea = 0;
  for (const polygon of polygons) {
    const [outer, ...holes] = polygon;
    const area = ringArea(outer) - holes.reduce((sum, ring) => sum + ringArea(ring), 0);
    if (area > largestArea) {
      largest = polygon;
      largestArea = area;
    }
    for (const [lon, lat] of outer) {
      bbox[0] = Math.min(bbox[0], lon);
      bbox[1] = Math.min(bbox[1], lat);
      bbox[2] = Math.max(bbox[2], lon);
      bbox[3] = Math.max(bbox[3], lat);
    }
  }
  if (!largest) throw new Error(`Empty region after simplification: ${code}`);
  const center = Array.from(polylabel(largest, 0.01), (n) => Number(n.toFixed(5)));
  return { code, name, center, bbox };
}

export async function buildGeo(source) {
  const input = sourceInContractOrder(source);
  const result = await mapshaper.applyCommands([
    '-i', 'source.geojson', '-simplify', '8%', 'keep-shapes',
    '-o', 'regions.geojson', 'precision=0.0001', 'geojson-type=FeatureCollection',
    '-dissolve', '-o', 'outline.geojson', 'precision=0.0001', 'geojson-type=FeatureCollection',
  ], { 'source.geojson': input });
  const regions = JSON.parse(result['regions.geojson']);
  if (Buffer.byteLength(JSON.stringify(regions) + '\n') > 400 * 1024) throw new Error('regions.geojson exceeds 400 KiB');
  const outline = JSON.parse(result['outline.geojson']);
  outline.features[0].properties = {};
  return { regions, outline, meta: regions.features.map(regionMeta) };
}
