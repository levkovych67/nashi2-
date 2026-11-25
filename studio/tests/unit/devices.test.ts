import { expect, test } from 'vitest'
import { DEVICES, frameScale } from '../../src/shell/devices'

test('fits a phone when height constrains it', () => {
  expect(frameScale('mobile', { width: 300, height: 500 })).toBeCloseTo(500 / 844)
})

test('fits a desktop preview by width', () => {
  expect(frameScale('desktop', { width: 720, height: 600 })).toBe(0.5)
})

test('never enlarges a natural viewport or divides by zero', () => {
  expect(frameScale('mobile', { width: 2000, height: 2000 })).toBe(1)
  expect(frameScale('desktop', { width: 0, height: 100 })).toBe(0)
})

test('keeps the minimum mobile and desktop reference sizes', () => {
  expect(DEVICES.mobileMin).toEqual({ width: 360, height: 640 })
  expect(DEVICES.desktopMin).toEqual({ width: 1024, height: 768 })
})
