export interface FieldError {
  field: string
  message: string
}

export class ApiProblem extends Error {
  status: number
  code: string
  errors: FieldError[]
  retryAfterSec?: number

  constructor(init: { status: number; code: string; message?: string; errors?: FieldError[]; retryAfterSec?: number }) {
    super(init.message ?? init.code)
    this.name = 'ApiProblem'
    this.status = init.status
    this.code = init.code
    this.errors = init.errors ?? []
    this.retryAfterSec = init.retryAfterSec
  }
}

export type QueryValue = string | string[] | number | undefined
export type ApiInit = RequestInit & { query?: Record<string, QueryValue> }

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v2'

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const origin = globalThis.location?.origin ?? 'http://localhost'
  const url = new URL(API_BASE + path, origin)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined) continue
    for (const v of Array.isArray(value) ? value : [value]) url.searchParams.append(key, String(v))
  }
  return url.toString()
}

export async function apiFetch<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { query, headers, ...rest } = init
  let res: Response
  try {
    res = await fetch(buildUrl(path, query), { ...rest, headers: { Accept: 'application/json', ...headers } })
  } catch {
    throw new ApiProblem({ status: 0, code: 'NETWORK' })
  }
  if (res.status === 204) return undefined as T
  if (!res.ok) {
    const retryAfter = Number(res.headers.get('Retry-After'))
    const retryAfterSec = Number.isFinite(retryAfter) && res.headers.has('Retry-After') ? retryAfter : undefined
    const isProblem = res.headers.get('Content-Type')?.includes('application/problem+json')
    const body = isProblem ? await res.json().catch(() => null) : null
    throw new ApiProblem({
      status: res.status,
      code: body?.code ?? (res.status >= 500 ? 'INTERNAL' : 'UNKNOWN'),
      message: body?.detail ?? body?.title,
      errors: body?.errors,
      retryAfterSec,
    })
  }
  return (await res.json()) as T
}
