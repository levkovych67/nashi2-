export async function sendSelectionToTeam(
  url: string | undefined,
  payload: unknown,
  fetchFn = fetch,
): Promise<'sent' | 'disabled' | 'failed'> {
  if (!url) return 'disabled'
  try {
    const response = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      credentials: 'omit',
    })
    return response.ok ? 'sent' : 'failed'
  } catch {
    return 'failed'
  }
}
