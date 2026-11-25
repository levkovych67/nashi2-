import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createCopy } from '../../core/i18n/copy'
import { ImagesField } from './ImagesField'

const compress = vi.hoisted(() => vi.fn())
vi.mock('../../core/submission/compressImage', () => ({ compressImage: compress }))

const t = createCopy({})
const MB = 1024 * 1024
const file = (name: string, size: number, type = 'image/jpeg') => new File([new Uint8Array(size)], name, { type })

function Harness({ maxTotalMb = 55 }: { maxTotalMb?: number }) {
  const [images, setImages] = useState<File[]>([])
  return (
    <ImagesField id="i" label="L" hint="H" images={images} onChange={setImages} onBusy={() => {}} max={5} maxMb={10} maxTotalMb={maxTotalMb} rowErrors={{}} />
  )
}
const pick = (...files: File[]) => fireEvent.change(screen.getByLabelText('L'), { target: { files } })

beforeEach(() => {
  URL.createObjectURL = () => 'blob:test'
  URL.revokeObjectURL = () => {}
})

afterEach(() => {
  cleanup()
  compress.mockReset()
})

describe('ImagesField size limits apply to the compressed file', () => {
  it('accepts a >10 MB original that compresses under the limit', async () => {
    render(<Harness />)
    compress.mockResolvedValue(file('phone.jpg', 1 * MB))
    pick(file('phone.jpg', 12 * MB))
    await waitFor(() => expect(screen.getByText(/1\. phone\.jpg/)).toBeTruthy())
    expect(screen.queryByTestId('photo-problems')).toBeNull()
  })

  it('rejects a photo that is still >10 MB after compression', async () => {
    render(<Harness />)
    compress.mockResolvedValue(file('huge.jpg', 11 * MB))
    pick(file('huge.jpg', 30 * MB))
    expect((await screen.findByTestId('photo-problems')).textContent).toContain(t('submit.error.imageSize'))
    expect(screen.queryByText(/1\. huge\.jpg/)).toBeNull()
  })

  it('still rejects the wrong type before compressing', async () => {
    render(<Harness />)
    pick(file('a.gif', 10, 'image/gif'))
    expect((await screen.findByTestId('photo-problems')).textContent).toContain(t('submit.error.imageType'))
    expect(compress).not.toHaveBeenCalled()
  })

  it('stops adding photos once the total would pass the cap', async () => {
    render(<Harness maxTotalMb={13} />)
    compress.mockImplementation(async (f: File) => file(f.name, 6 * MB))
    pick(file('a.jpg', 1), file('b.jpg', 1), file('c.jpg', 1))
    await waitFor(() => expect(screen.getByTestId('photo-problems')).toBeTruthy())
    expect(screen.getByTestId('photo-problems').textContent).toContain(`c.jpg: ${t('submit.error.imagesTotal')}`)
    expect(screen.getByText(/2\. b\.jpg/)).toBeTruthy()
    expect(screen.queryByText(/3\. c\.jpg/)).toBeNull()
  })
})
