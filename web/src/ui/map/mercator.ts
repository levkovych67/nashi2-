// Web Mercator in MapLibre's terms: world coordinates in [0, 1], 512 px tiles.
export const TILE_SIZE = 512

export const mercatorX = (lon: number) => (lon + 180) / 360

export function mercatorY(lat: number): number {
  const sin = Math.sin((lat * Math.PI) / 180)
  return 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)
}

export const lonOfX = (x: number) => x * 360 - 180
export const latOfY = (y: number) => (360 / Math.PI) * Math.atan(Math.exp((0.5 - y) * 2 * Math.PI)) - 90

/** Screen distance in px between two points at `zoom`. */
export function pixelDistance(lon1: number, lat1: number, lon2: number, lat2: number, zoom: number): number {
  const world = TILE_SIZE * 2 ** zoom
  return Math.hypot((mercatorX(lon1) - mercatorX(lon2)) * world, (mercatorY(lat1) - mercatorY(lat2)) * world)
}
