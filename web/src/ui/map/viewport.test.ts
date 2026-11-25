import { describe, expect, it, vi } from 'vitest'
import { UKRAINE_BBOX, type CameraMove } from '../../core/map/camera'
import { fitZoom, mapLimits, moveCamera, MIN_ZOOM } from './viewport'

const pad = { top: 40, right: 40, bottom: 40, left: 40 }
const none = { top: 0, right: 0, bottom: 0, left: 0 }
const worldPx = (zoom: number) => 512 * 2 ** zoom
const lonSpanPx = ([w, , e]: number[], zoom: number) => ((e - w) / 360) * worldPx(zoom)

describe('fitZoom', () => {
  it('is the zoom at which the bbox fills the padded viewport (512 px tiles)', () => {
    // 100 px wide at zoom 0, so it fills 200 px at zoom 1; the latitude span is negligible
    const bbox = [0, -0.0001, (360 * 100) / 512, 0.0001] as [number, number, number, number]
    expect(fitZoom(bbox, { width: 200, height: 1000 }, none)).toBeCloseTo(1, 6)
    expect(fitZoom(bbox, { width: 280, height: 1000 }, pad)).toBeCloseTo(1, 6)
  })
})

describe('mapLimits', () => {
  it('on a phone lowers the minimum zoom so that the whole country fits (D-10)', () => {
    const phone = { width: 390, height: 540 }
    const { minZoom, maxBounds } = mapLimits(phone, pad)
    expect(minZoom).toBeLessThan(MIN_ZOOM)
    expect(minZoom).toBeCloseTo(fitZoom(UKRAINE_BBOX, phone, pad), 6)
    // MapLibre zooms in when the viewport is larger than maxBounds: the bounds must hold the overview viewport
    expect(lonSpanPx(maxBounds, minZoom)).toBeGreaterThanOrEqual(phone.width)
  })

  it('on desktop keeps the configured minimum zoom', () => {
    expect(mapLimits({ width: 1440, height: 780 }, pad).minZoom).toBe(MIN_ZOOM)
  })

  it('maxBounds always contain Ukraine ± 1°', () => {
    for (const size of [{ width: 390, height: 540 }, { width: 1440, height: 780 }, { width: 360, height: 300 }]) {
      const [w, s, e, n] = mapLimits(size, pad).maxBounds
      const [uw, us, ue, un] = UKRAINE_BBOX
      expect(w).toBeLessThanOrEqual(uw - 1)
      expect(s).toBeLessThanOrEqual(us - 1)
      expect(e).toBeGreaterThanOrEqual(ue + 1)
      expect(n).toBeGreaterThanOrEqual(un + 1)
    }
  })
})

describe('moveCamera', () => {
  const fake = () => ({
    flyTo: vi.fn(),
    fitBounds: vi.fn(),
    cameraForBounds: vi.fn(() => ({ center: { lng: 24, lat: 49.7 } as never, zoom: 6.3 })),
  })
  const fit: CameraMove = { type: 'fitBounds', bounds: [[22, 48], [25, 50]], padding: pad, duration: 1200 }

  it('flies to flyTo moves', () => {
    const map = fake()
    moveCamera(map, { type: 'flyTo', center: [24, 49], zoom: 12, duration: 600 })
    expect(map.flyTo).toHaveBeenCalledWith({ center: [24, 49], zoom: 12, duration: 600, essential: true })
  })

  it('fits bounds as given without a floor', () => {
    const map = fake()
    moveCamera(map, fit)
    expect(map.fitBounds).toHaveBeenCalledWith([[22, 48], [25, 50]], { padding: pad, duration: 1200, essential: true })
  })

  it('with a floor zooms in at least to it, so opening a region reaches the next level', () => {
    const map = fake()
    moveCamera(map, fit, 7)
    expect(map.cameraForBounds).toHaveBeenCalledWith([[22, 48], [25, 50]], { padding: pad })
    expect(map.flyTo).toHaveBeenCalledWith({ center: { lng: 24, lat: 49.7 }, zoom: 7, duration: 1200, essential: true })
    expect(map.fitBounds).not.toHaveBeenCalled()
  })
})
