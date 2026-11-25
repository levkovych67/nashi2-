import { setupServer } from 'msw/node'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { http, HttpResponse } from 'msw'
import { handlers } from './handlers'

// The app fetches the geometry from /geo; tests read the same files from the contracts.
const geo = (name: string) => http.get(`*/geo/${name}`, () => HttpResponse.text(readFileSync(resolve(process.cwd(), '../contracts/geo', name), 'utf8'), { headers: { 'Content-Type': 'application/json' } }))

export const server = setupServer(...handlers, geo('regions.geojson'), geo('ukraine-outline.geojson'))
