import { ApiProblem, apiFetch } from '../../api/client'
import type { SendResult } from './batcher'

const DEFAULT_RETRY_AFTER_MS = 1000

/** Maps the likes API onto the batcher contract: 2xx -> ok, 429 -> back-off, anything else throws. */
export async function sendLikes(id: string, count: number, opts: { keepalive: boolean }): Promise<SendResult> {
  try {
    const body = await apiFetch<{ likesCount: number }>(`/musicians/${encodeURIComponent(id)}/likes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ count }),
      keepalive: opts.keepalive,
    })
    return { ok: true, likesCount: body.likesCount }
  } catch (e) {
    if (e instanceof ApiProblem && e.status === 429) {
      return { ok: false, retryAfterMs: e.retryAfterSec !== undefined ? e.retryAfterSec * 1000 : DEFAULT_RETRY_AFTER_MS }
    }
    throw e
  }
}
