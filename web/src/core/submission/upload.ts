import { API_BASE, ApiProblem } from '../../api/client'
import type { components } from '../../api/types.gen'
import type { SubmissionFormValues } from './schema'

export type SubmissionAccepted = components['schemas']['SubmissionAccepted']

/**
 * POST /submissions as multipart (`data` JSON part + `images`). XMLHttpRequest, because fetch cannot report upload
 * progress. `faxNumber` is the bot trap: the real form always sends it empty.
 */
export function uploadSubmission(
  values: SubmissionFormValues & { faxNumber?: string },
  onProgress: (ratio: number) => void,
  signal?: AbortSignal,
): Promise<SubmissionAccepted> {
  return new Promise((resolve, reject) => {
    const { images, faxNumber = '', ...data } = values
    const body = new FormData()
    body.append('data', new Blob([JSON.stringify({ ...data, faxNumber })], { type: 'application/json' }), 'data.json')
    for (const image of images) body.append('images', image, image.name)

    const xhr = new XMLHttpRequest()
    const abort = () => xhr.abort()
    const done = () => signal?.removeEventListener('abort', abort)
    xhr.open('POST', `${API_BASE}/submissions`)
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && e.total > 0) onProgress(e.loaded / e.total)
    }
    xhr.onload = () => {
      done()
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as SubmissionAccepted)
        } catch {
          reject(new ApiProblem({ status: xhr.status, code: 'INTERNAL' }))
        }
        return
      }
      let problem: { code?: string; detail?: string; title?: string; errors?: ApiProblem['errors'] } | null = null
      if (xhr.getResponseHeader('content-type')?.includes('application/problem+json')) {
        try {
          problem = JSON.parse(xhr.responseText)
        } catch {
          problem = null
        }
      }
      const retryAfter = Number(xhr.getResponseHeader('retry-after'))
      reject(
        new ApiProblem({
          status: xhr.status,
          code: problem?.code ?? (xhr.status >= 500 ? 'INTERNAL' : 'UNKNOWN'),
          message: problem?.detail ?? problem?.title,
          errors: problem?.errors,
          retryAfterSec: Number.isFinite(retryAfter) && xhr.getResponseHeader('retry-after') ? retryAfter : undefined,
        }),
      )
    }
    xhr.onerror = () => {
      done()
      reject(new ApiProblem({ status: 0, code: 'NETWORK' }))
    }
    xhr.onabort = () => {
      done()
      reject(new DOMException('Aborted', 'AbortError'))
    }
    if (signal?.aborted) return reject(new DOMException('Aborted', 'AbortError'))
    signal?.addEventListener('abort', abort, { once: true })
    xhr.send(body)
  })
}
