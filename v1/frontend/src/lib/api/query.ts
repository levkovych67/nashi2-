// Serialises request params; array values repeat the key (types=A&types=B).
export function buildQuery(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  const isSet = (v: unknown) => v !== undefined && v !== null && v !== '';
  for (const [key, value] of Object.entries(params)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (isSet(item)) search.append(key, String(item));
      }
    } else if (isSet(value)) {
      search.append(key, String(value));
    }
  }
  return search.toString();
}
