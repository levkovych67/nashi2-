// Asset paths in the portable fixture bundle start at the demo origin.
// An API response represents these same assets with absolute media URLs.
export function parseFixtureResponse(json, origin) {
  const base = new URL(origin);
  return JSON.parse(json, (key, value) => {
    if ((key === 'url' || key === 'thumbUrl') && typeof value === 'string'
        && /^\/fixtures\/(audio\/clip-0[1-6]\.wav|img\/extra-[1-3]\.svg)$/.test(value)) {
      return new URL(value, base.origin).href;
    }
    return value;
  });
}
