import { targetSize } from './imageSize'

const KEEP_BYTES = 2 * 1024 * 1024

/**
 * Downscales a photo to `maxSide` and re-encodes it as JPEG. A file that already fits and is small is returned as is.
 * Rejects when the browser cannot decode the file (which also catches a non-image renamed to .jpg).
 */
export async function compressImage(file: File, maxSide = 2048, quality = 0.85): Promise<File> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const { width, height } = targetSize(bitmap.width, bitmap.height, maxSide)
    if (width === bitmap.width && height === bitmap.height && file.size <= KEEP_BYTES) return file
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('canvas unavailable')
    ctx.fillStyle = '#fff' // JPEG has no alpha: transparent PNG areas become white, not black
    ctx.fillRect(0, 0, width, height)
    ctx.drawImage(bitmap, 0, 0, width, height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (!blob) throw new Error('encode failed')
    return new File([blob], file.name.replace(/\.[^.]*$/, '') + '.jpg', { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}
