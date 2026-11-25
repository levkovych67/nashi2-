// Thousands of artists stay readable: nearby pins merge into a bubble with a count, and only what is on screen renders.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPinIndex, getMapItems, getExpansionZoom, formatClusterCount, type MapItem } from '../src/features/map/utils/clusterPins.ts';
import { groupPinsByLocation } from '../src/lib/utils/groupPinsByLocation.ts';

const UKRAINE: [number, number, number, number] = [22, 44, 41, 53];
const KYIV = { latitude: 50.45, longitude: 30.52 };

function pin(i: number, latitude: number, longitude: number) {
  return { id: `p${i}`, name: `Артист ${i}`, latitude, longitude, type: 'ARTIST' as const, slug: `a-${i}` };
}

function aroundKyiv(n: number) {
  // spread within ~3 km
  return Array.from({ length: n }, (_, i) => pin(i, KYIV.latitude + (i % 10) * 0.002, KYIV.longitude + Math.floor(i / 10) * 0.003));
}

function total(items: MapItem[]) {
  return items.reduce((sum, item) => sum + (item.kind === 'cluster' ? item.count : item.group.pins.length), 0);
}

test('nearby artists merge into one bubble on the country view', () => {
  const index = createPinIndex(groupPinsByLocation(aroundKyiv(50)));
  const items = getMapItems(index, UKRAINE, 6);

  assert.equal(items.length, 1);
  assert.equal(items[0].kind, 'cluster');
  assert.equal(total(items), 50);
});

test('zoomed in close, every artist has its own pin', () => {
  const index = createPinIndex(groupPinsByLocation(aroundKyiv(50)));
  const items = getMapItems(index, UKRAINE, 17);

  assert.equal(items.length, 50);
  assert.ok(items.every((item) => item.kind === 'group'));
});

test('artists at the very same point stay one stack at any zoom and count fully', () => {
  const samePoint = Array.from({ length: 300 }, (_, i) => pin(i, KYIV.latitude, KYIV.longitude));
  const index = createPinIndex(groupPinsByLocation(samePoint));

  const close = getMapItems(index, UKRAINE, 17);
  assert.equal(close.length, 1);
  assert.equal(close[0].kind === 'group' && close[0].group.pins.length, 300);

  const far = getMapItems(index, UKRAINE, 6);
  assert.equal(total(far), 300);
});

test('a stack and a neighbour merge into one bubble counting all their artists', () => {
  const pins = [...Array.from({ length: 300 }, (_, i) => pin(i, KYIV.latitude, KYIV.longitude)), pin(999, 50.46, 30.53)];
  const items = getMapItems(createPinIndex(groupPinsByLocation(pins)), UKRAINE, 6);

  assert.equal(items.length, 1);
  assert.equal(items[0].kind === 'cluster' && items[0].count, 301);
});

test('only artists inside the visible area are returned', () => {
  const pins = [pin(1, 49.84, 24.03), pin(2, 46.48, 30.72)];
  const lvivOnly: [number, number, number, number] = [23, 49, 25, 50.5];

  const items = getMapItems(createPinIndex(groupPinsByLocation(pins)), lvivOnly, 10);

  assert.equal(items.length, 1);
  assert.equal(items[0].kind === 'group' && items[0].group.pins[0].id, 'p1');
});

test('clicking a bubble zooms in far enough to split it', () => {
  const index = createPinIndex(groupPinsByLocation(aroundKyiv(50)));
  const [cluster] = getMapItems(index, UKRAINE, 6);

  assert.ok(cluster.kind === 'cluster' && getExpansionZoom(index, cluster.id) > 6);
});

test('big counts are shortened the Ukrainian way', () => {
  assert.equal(formatClusterCount(7), '7');
  assert.equal(formatClusterCount(999), '999');
  assert.equal(formatClusterCount(1234), '1,2 тис.');
  assert.equal(formatClusterCount(4000), '4 тис.');
});

test('the bubble is announced with the right Ukrainian plural', async () => {
  const { clusterLabel } = await import('../src/features/map/utils/clusterPins.ts');
  assert.equal(clusterLabel(2), '2 позначки — наблизити');
  assert.equal(clusterLabel(5), '5 позначок — наблизити');
  assert.equal(clusterLabel(12), '12 позначок — наблизити');
  assert.equal(clusterLabel(21), '21 позначка — наблизити');
  assert.equal(clusterLabel(1234), '1234 позначки — наблизити');
});
