import { afterEach, describe, expect, it, vi } from 'vitest'
import { compressImage } from './compressImage'

function fakeCanvas(blob: Blob | null) {
  const canvas = {
    width: 0,
    height: 0,
    sizes: [] as Array<[number, number]>,
    getContext: () => ({ fillStyle: '', fillRect() {}, drawImage() {} }),
    toBlob(cb: (b: Blob | null) => void) {
      canvas.sizes.push([canvas.width, canvas.height])
      cb(blob)
    },
  }
  return canvas
}

describe('compressImage', () => {
  afterEach(() => vi.restoreAllMocks())

  function stub(blob: Blob | null) {
    const close = vi.fn()
    vi.stubGlobal('createImageBitmap', async () => ({ width: 4000, height: 3000, close }))
    const canvas = fakeCanvas(blob)
    const create = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation(((tag: string) =>
      tag === 'canvas' ? canvas : create(tag)) as typeof document.createElement)
    return { canvas, close }
  }

  it('downscales to 2048 px as JPEG and releases the canvas memory afterwards (Safari keeps it otherwise)', async () => {
    const { canvas, close } = stub(new Blob(['x'], { type: 'image/jpeg' }))
    const out = await compressImage(new File([new Uint8Array(3 * 1024 * 1024)], 'photo.png', { type: 'image/png' }))
    expect(canvas.sizes).toEqual([[2048, 1536]])
    expect(out.name).toBe('photo.jpg')
    expect(out.type).toBe('image/jpeg')
    expect([canvas.width, canvas.height]).toEqual([0, 0])
    expect(close).toHaveBeenCalled()
  })

  it('releases the canvas also when encoding fails', async () => {
    const { canvas } = stub(null)
    await expect(compressImage(new File([new Uint8Array(3 * 1024 * 1024)], 'a.png'))).rejects.toThrow('encode failed')
    expect([canvas.width, canvas.height]).toEqual([0, 0])
  })
})
