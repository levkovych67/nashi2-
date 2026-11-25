import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import { clusterCamera, placeCamera, regionsCamera, UKRAINE_BBOX, ukraineCamera } from './camera'

type RegionStat = components['schemas']['RegionStat']
const insets = { top: 10, right: 20, bottom: 30, left: 40 }
const region = (code: RegionStat['code'], bbox: number[]): RegionStat => ({ code, name: code, center: [0, 0], bbox, count: 1 })

describe('camera', () => {
  it('regionsCamera fits union bbox with padding 40 + insets', () => {
    const move = regionsCamera([region('LVIV', [23, 48.5, 25, 50.5]), region('ODESA', [29, 45, 31, 47])], insets, false)
    expect(move).toEqual({
      type: 'fitBounds',
      bounds: [[23, 45], [31, 50.5]],
      padding: { top: 50, right: 60, bottom: 70, left: 80 },
      duration: 1200,
    })
  })

  it('regionsCamera without regions falls back to Ukraine', () => {
    expect(regionsCamera([], insets, false)).toEqual(ukraineCamera(insets, false))
  })

  it('ukraineCamera fits the national bbox', () => {
    const [w, s, e, n] = UKRAINE_BBOX
    expect(ukraineCamera(insets, false)).toMatchObject({ type: 'fitBounds', bounds: [[w, s], [e, n]], duration: 1200 })
  })

  it('reduced motion gives duration 0', () => {
    expect(ukraineCamera(insets, true).duration).toBe(0)
    expect(regionsCamera([region('LVIV', [23, 48, 25, 50])], insets, true).duration).toBe(0)
    expect(placeCamera(24, 49, true).duration).toBe(0)
    expect(clusterCamera(24, 49, 9, true).duration).toBe(0)
  })

  it('placeCamera flies to zoom 12 in 1200 ms', () => {
    expect(placeCamera(24, 49, false)).toEqual({ type: 'flyTo', center: [24, 49], zoom: 12, duration: 1200 })
  })

  it('clusterCamera caps zoom at 17', () => {
    expect(clusterCamera(24, 49, 9, false)).toEqual({ type: 'flyTo', center: [24, 49], zoom: 9, duration: 600 })
    expect(clusterCamera(24, 49, 19, false).zoom).toBe(17)
  })
})
