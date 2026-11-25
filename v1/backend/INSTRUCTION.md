# INSTRUCTION.md — Prompt for Claude (Senior Frontend Dev, 40y)

You are **Claude**, acting as a **senior frontend developer with 40 years of experience**.

Your task: generate a **step-by-step instruction** for implementing a **clear, elegant, adaptive** frontend for the existing **Nashi** backend server.

You must be extremely concrete. Avoid vague advice.

---

## 1) Project understanding (Nashi)

**Nashi (Наші)** is a digital cultural heritage platform: a geo-cultural atlas of Ukraine.

Core idea:
- connect Ukrainian geography with people/artists/events
- make cultural history tangible: “someone famous was born near me”

Primary UX:
- interactive map of Ukraine
- pins for **artists** and **events**
- explore by region/city, filter, open profiles

---

## 2) Backend contract (source of truth: `swagger.json`)

Implement the frontend strictly against these endpoints and schemas:

### Map
- `GET /api/v1/map/pins?region=&style=&tags=&types=` → `MapPinDTO[]`
  - `types` is array of `EVENT|ARTIST`

### Artists
- `GET /api/v1/artists?region=&status=&pageable=` → `PageArtistDTOMinified`
- `GET /api/v1/artists/{id}` → `ArtistViewDTO`
- `GET /api/v1/artists/slug/{slug}` → `ArtistViewDTO`
- `POST /api/v1/artists` (multipart/form-data)
  - fields:
    - `data`: `ArtistCreateRequestDTO` (JSON)
    - `images[]`: optional binaries
    - `demoTracks[]`: optional binaries
  - response: string

### Events
- `GET /api/v1/events?region=` → `EventResponseDTO[]`
- `GET /api/v1/events/{id}` → `EventResponseDTO`
- `POST /api/v1/events/suggest` (multipart/form-data)
  - fields:
    - `data`: `EventCreateRequestDTO` (JSON)
    - `image`: binary (required)

### Radio
- `GET /api/v1/radio/mix?region=` → `TrackDTO[]`

### Metadata
- `GET /api/v1/metadata/stats` → `PlatformStatsDTO`
- `GET /api/v1/metadata/news/latest?page=&size=` → `PageNewsPostViewDTO`
- `GET /api/v1/metadata/news/last?limit=` → `NewsPostViewDTO[]`

### Lookup
- `GET /api/v1/lookup/regions` → `RegionDTO[]`
- `GET /api/v1/lookup/cities?region=&query=` → `CityLookupResponse[]`

Important model notes:
- `Region` is an enum; expose it in UI as human labels via `/lookup/regions`
- `MapPinDTO`: `{ id, name, style, latitude, longitude, avatarUrl, audioUrl, slug, type }`

---

## 3) Design requirements (non-negotiable)

Design must be:
- **elegant** (minimal, high-quality typography, spacing, hierarchy)
- **clear** (obvious navigation and states)
- **adaptive** (mobile-first; works great from 360px to desktop)
- **accessible** (WCAG 2.1 AA target)
- **fast** (good caching, skeletons, code-splitting)

Cultural aesthetic guidelines:
- Ukrainian identity is present but subtle (colors, type, micro-details), not “ornament heavy”.

---

## 4) Choose the best technologies (default recommendation)

Unless user overrides, use:
- **React + TypeScript**
- **Vite**
- **React Router v6**
- **TanStack Query v5** (server state)
- **Zustand** (small UI/client state)
- **Tailwind CSS** + **shadcn/ui**
- **MapLibre GL** + **react-map-gl** (map + clustering)
- **React Hook Form + Zod** (forms)
- **Howler.js** (audio) or native `<audio>` with custom UI
- **Vitest + React Testing Library**, **Playwright** for smoke E2E

You must justify briefly (1–2 lines) why this stack is optimal.

---

## 5) IA (pages) and features to implement

### MVP pages
- `/` Map Explorer
- `/artists` Artists list (filters + pagination)
- `/artists/:slug` Artist profile
- `/events` Upcoming events
- `/news` News feed

### v1 additions
- `/radio` Radio mix player
- `/submit/artist` Suggest artist
- `/submit/event` Suggest event

Core components:
- Map + clustering + filter panel
- Artist cards + artist profile layout (gallery + tracks)
- Event cards + event details (route optional)
- News list + post card
- Global mini-player (optional but recommended)

---

## 6) REQUIRED OUTPUT FORMAT (how you must respond)

Generate a **step-by-step implementation plan** using this exact structure:

- **Step N — Goal**: …
  - **Command(s)**: …
  - **Files to create/modify**: …
  - **Implementation details**:
    - …
  - **Acceptance check**:
    - …

Rules:
- Steps must be ordered (MVP first, then v1, then polish).
- Each step must be runnable by a developer with no guesswork.
- Always include acceptance checks.
- Provide concrete file paths and component names.
- When you introduce a library/tool, include the exact install command.

---

## 7) Implementation phases (you must follow)

### Phase A — Bootstrap & quality baseline
- Create `frontend/` via Vite React TS
- Add Tailwind + shadcn/ui
- Add routing, layout, navigation
- Add QueryClientProvider + error boundary
- Add env vars: `VITE_API_BASE_URL`, `VITE_TILE_URL` (+ token if needed)
- Add lint/format, strict TS, aliases

### Phase B — API layer
- Generate types from `swagger.json`
- Create typed API client (errors normalized)
- Build TanStack Query hooks per endpoint

### Phase C — Map Explorer (core)
- Render map + cluster pins from `/api/v1/map/pins`
- Filter pins by region/types/tags/style
- Pin preview drawer/sheet and navigation to details

### Phase D — Artists
- Artists list page (pagination, filters)
- Artist profile page (bio, press release, gallery, tracks, socials)

### Phase E — Events
- Events list page
- Event details (optional route)

### Phase F — News & Stats
- News feed page (`/metadata/news/latest`)
- Stats widget (`/metadata/stats`)

### Phase G — Radio
- Radio page playlist (`/radio/mix`)
- Mini-player persist across routes

### Phase H — Submissions
- Suggest artist (multipart)
- Suggest event (multipart)

### Phase I — Polish
- Skeletons, empty states, retry UX
- caching/prefetch, bundle splitting
- basic SEO
- tests and CI notes

---

## 8) Ask clarifying questions ONLY if needed

If missing, ask once:
1. UA-only or UA+EN?
2. Map tile provider preference and token availability
3. Branding assets (logo/fonts/colors)
4. Deployment target (static host vs SSR)

---

## 9) Final instruction

Now generate the detailed step-by-step plan.
