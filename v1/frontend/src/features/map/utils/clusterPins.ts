import Supercluster from 'supercluster';
import type { PinGroup } from '@/lib/utils/groupPinsByLocation';

interface GroupProps {
  groupIndex: number;
  count: number;
}

interface ClusterProps {
  count: number;
}

export interface PinIndex {
  cluster: Supercluster<GroupProps, ClusterProps>;
  groups: PinGroup[];
}

export type MapItem =
  | { kind: 'cluster'; id: number; latitude: number; longitude: number; count: number }
  | { kind: 'group'; group: PinGroup };

/** Pixels around a point that merge into one bubble; past MAX_CLUSTER_ZOOM every pin shows on its own. */
const CLUSTER_RADIUS = 60;
const MAX_CLUSTER_ZOOM = 16;

/**
 * Builds a spatial index over pin groups (pins that share exact coordinates are already one group), so the map only
 * renders the bubbles and pins inside the visible area.
 */
export function createPinIndex(groups: PinGroup[]): PinIndex {
  const cluster = new Supercluster<GroupProps, ClusterProps>({
    radius: CLUSTER_RADIUS,
    maxZoom: MAX_CLUSTER_ZOOM,
    map: (props) => ({ count: props.count }),
    reduce: (accumulated, props) => {
      accumulated.count += props.count;
    },
  });
  cluster.load(
    groups.map((group, groupIndex) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [group.longitude, group.latitude] },
      properties: { groupIndex, count: group.pins.length },
    })),
  );
  return { cluster, groups };
}

/** Bubbles and pin groups inside `bbox` ([west, south, east, north]) at `zoom`. */
export function getMapItems(index: PinIndex, bbox: [number, number, number, number], zoom: number): MapItem[] {
  return index.cluster.getClusters(bbox, Math.floor(zoom)).map((feature) => {
    const [longitude, latitude] = feature.geometry.coordinates;
    const props = feature.properties;
    if ('cluster' in props && props.cluster) {
      return { kind: 'cluster', id: props.cluster_id, latitude, longitude, count: props.count };
    }
    return { kind: 'group', group: index.groups[(props as GroupProps).groupIndex] };
  });
}

/** The zoom at which a bubble splits into smaller ones or single pins. */
export function getExpansionZoom(index: PinIndex, clusterId: number): number {
  return index.cluster.getClusterExpansionZoom(clusterId);
}

/** 7 → "7", 1234 → "1,2 тис.", 4000 → "4 тис." */
export function formatClusterCount(count: number): string {
  if (count < 1000) return String(count);
  const thousands = (Math.round(count / 100) / 10).toString().replace('.', ',');
  return `${thousands} тис.`;
}

/** Screen-reader label: "2 позначки — наблизити", "5 позначок — наблизити", "21 позначка — наблизити". */
export function clusterLabel(count: number): string {
  const lastTwo = count % 100;
  const last = count % 10;
  let word = 'позначок';
  if (last === 1 && lastTwo !== 11) word = 'позначка';
  else if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) word = 'позначки';
  return `${count} ${word} — наблизити`;
}
