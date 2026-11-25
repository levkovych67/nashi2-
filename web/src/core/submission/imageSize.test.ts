import { describe, expect, it } from 'vitest'
import { targetSize } from './imageSize'

describe('targetSize', () => {
  it('scales the long side down and keeps the aspect', () => {
    expect(targetSize(4032, 3024, 2048)).toEqual({ width: 2048, height: 1536 })
    expect(targetSize(3024, 4032, 2048)).toEqual({ width: 1536, height: 2048 })
  })
  it('never upscales', () => {
    expect(targetSize(800, 600, 2048)).toEqual({ width: 800, height: 600 })
    expect(targetSize(2048, 2048, 2048)).toEqual({ width: 2048, height: 2048 })
  })
})
