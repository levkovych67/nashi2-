export function variantUrl(origin: string, decisionId: string, variantId: string, identity: string): string {
  const path = '/v/' + [decisionId, variantId].map(encodeURIComponent).join('/')
  const url = new URL(path, origin)
  url.searchParams.set('identity', identity)
  return url.href
}
