# Frontend Implementation Instructions for Nashi Cultural Platform

**Target AI**: Claude (acting as Senior Frontend Developer with 40 years of experience)

**Your Role**: You are an expert frontend developer. Your task is to **generate a detailed, step-by-step implementation plan** for building an elegant, clear, and adaptive frontend for the Nashi platform.

---

## PROJECT CONTEXT

### About Nashi (Наші - "Ours")

Nashi is a digital cultural heritage platform that maps the creative history of Ukraine through an interactive geographical interface.

**Mission**: Rediscover and visualize the origins of Ukrainian artists, musicians, and cultural icons by connecting them to their birthplaces and significant locations.

**Core Value Proposition**: Transform abstract cultural history into tangible, personal connections (e.g., "This famous painter was born in my village").

### Target Users
1. **Students & Educators** - Cultural education and history lessons
2. **Tourists** - Exploring cultural history of Ukrainian cities
3. **Art Enthusiasts** - Discovering niche or local Ukrainian talent
4. **Local Artists** - (Future) Showcasing their own work

### Technical Context
- **Backend**: Spring Boot REST API (already implemented)
- **Database**: MongoDB with geospatial indexing
- **Storage**: AWS S3 for images and audio files
- **API Documentation**: Available via Swagger/OpenAPI (`swagger.json` provided)

---

## BACKEND API OVERVIEW

The backend provides the following key endpoints (see `swagger.json` for full details):

### Core Endpoints

**Map & Pins**
- `GET /api/v1/map/pins` - Get all map pins (artists + events) with filtering
  - Query params: `region`, `style`, `tags`, `types` (ARTIST|EVENT)
  - Returns: `MapPinDTO[]` with lat/lon, avatar, slug, type

**Artists**
- `GET /api/v1/artists` - List artists (paginated, filterable)
- `GET /api/v1/artists/{id}` - Get artist by ID
- `GET /api/v1/artists/slug/{slug}` - Get artist by slug (SEO-friendly)
- `POST /api/v1/artists` - Submit new artist (multipart: data + images[] + demoTracks[])

**Events**
- `GET /api/v1/events` - Get upcoming events (filterable by region)
- `GET /api/v1/events/{id}` - Get event details
- `POST /api/v1/events/suggest` - Submit event (multipart: data + image)

**Radio/Music**
- `GET /api/v1/radio/mix` - Get regional music mix (filterable by region)
  - Returns: `TrackDTO[]` with audio URLs

**Metadata**
- `GET /api/v1/metadata/stats` - Platform statistics (total/approved/pending artists)
- `GET /api/v1/metadata/news/latest` - News feed (paginated)
- `GET /api/v1/metadata/news/last` - Latest N news items

**Lookup/Utilities**
- `GET /api/v1/lookup/regions` - Get all Ukrainian regions with labels
- `GET /api/v1/lookup/cities` - Search cities by region

### Key Data Models

**MapPinDTO**
```typescript
{
  id: string;
  name: string;
  style: string;
  latitude: number;
  longitude: number;
  avatarUrl: string;
  audioUrl: string;
  slug: string;
  type: 'EVENT' | 'ARTIST';
}
```

**Region Enum**: 27 Ukrainian regions (KYIV_CITY, LVIV, ODESA, KHARKIV, etc.)

---

## DESIGN REQUIREMENTS

### Design Principles (Non-Negotiable)

1. **Elegant**
   - Minimal, sophisticated UI
   - High-quality typography and spacing
   - Clear visual hierarchy
   - Subtle Ukrainian cultural elements (colors, patterns) without kitsch

2. **Clear**
   - Intuitive navigation
   - Obvious interactive elements
   - Clear feedback on user actions
   - Meaningful loading states and error messages

3. **Adaptive**
   - Mobile-first approach
   - Responsive from 360px to large desktop
   - Touch-friendly on mobile, mouse-optimized on desktop
   - Progressive enhancement

4. **Accessible**
   - WCAG 2.1 AA compliance
   - Keyboard navigation
   - Screen reader support
   - High contrast, focus indicators

5. **Performant**
   - Lighthouse score ≥ 90 (performance, accessibility, best practices)
   - Fast initial load (<3s)
   - Smooth animations (60fps)
   - Optimized images and lazy loading

### Visual Design Guidelines

**Color Palette**
- Primary: Deep blue or warm amber (Ukrainian-inspired but subdued)
- Background: Near-white / soft gray (#FAFAFA, #F5F5F5)
- Text: Near-black (#1A1A1A, #333333)
- Accent: Ukrainian flag colors used sparingly

**Typography**
- Clear hierarchy (H1 → H6)
- Readable body text (16px base, 1.5-1.6 line-height)
- Ukrainian and Latin script support
- Consider: Inter, SF Pro, or system fonts for performance

**Spacing & Layout**
- 8px grid system
- Generous whitespace
- Max content width: 1100-1200px
- Mobile: single column, Desktop: 12-column grid

**Motion**
- Minimal, purposeful animations
- 150-250ms transitions
- Respect `prefers-reduced-motion`

---

## RECOMMENDED TECHNOLOGY STACK

You should use these technologies unless there's a compelling reason to choose alternatives:

### Core Framework
- **React 18+** with **TypeScript 5+**
- **Vite** - Fast build tool and dev server

### Routing & Navigation
- **React Router v6** - Standard, reliable routing

### State Management
- **TanStack Query v5** (React Query) - Server state, caching, data fetching
- **Zustand** - Lightweight client state (UI state, player state, filters)

### Styling
- **Tailwind CSS 3+** - Utility-first CSS framework
- **shadcn/ui** - High-quality React components (Radix UI primitives)
  - Provides: Dialog, Dropdown, Sheet, Tabs, Toast, Form components

### Map Integration
- **MapLibre GL JS** - Open-source map rendering (no token restrictions)
- **react-map-gl** - React wrapper for MapLibre
- **supercluster** - Efficient point clustering for map pins

### Forms & Validation
- **React Hook Form** - Performant form handling
- **Zod** - TypeScript-first schema validation

### HTTP & Data
- **Native fetch API** with typed wrapper
- Or **Axios** if you prefer (with interceptors)
- Generate TypeScript types from `swagger.json` using:
  - `openapi-typescript` or `swagger-typescript-api`

### Audio
- **Howler.js** - Reliable cross-browser audio library
- Or native `<audio>` with custom controls

### File Uploads
- **react-dropzone** - Drag & drop file uploads
- Show upload progress and file previews

### Utilities
- **date-fns** - Lightweight date formatting
- **clsx** / **cn** utility - Conditional class names

### Icons
- **Lucide React** - Beautiful, consistent icon set

### Testing
- **Vitest** - Fast unit testing (Vite-native)
- **React Testing Library** - Component testing
- **Playwright** - E2E testing (optional but recommended)

### Code Quality
- **ESLint** - Linting
- **Prettier** - Code formatting
- **TypeScript strict mode** - Type safety
- **Husky** - Git hooks (optional)

**Why This Stack?**
- Modern, well-maintained libraries
- Excellent TypeScript support
- Great developer experience
- Production-ready and performant
- Strong community and documentation

---

## INFORMATION ARCHITECTURE

### Site Structure (MVP → v1)

**MVP (Must Ship First)**
1. `/` - Map Explorer (home page)
2. `/artists` - Artists list with filters
3. `/artists/:slug` - Artist profile page
4. `/events` - Upcoming events list
5. `/news` - News feed

**v1 Additions**
6. `/radio` - Regional music player
7. `/submit/artist` - Suggest artist form
8. `/submit/event` - Suggest event form
9. `/about` - About the platform (optional)

### Navigation Structure

**Desktop**: Horizontal top navigation bar
- Logo (left)
- Map | Artists | Events | Radio | News | Submit (center/right)
- Search (icon, opens overlay)
- Language toggle UA/EN (future)

**Mobile**: Bottom tab bar
- Map | Artists | Events | News | Menu (hamburger)
  - Menu reveals: Radio, Submit, About

---

## KEY USER FLOWS

### Flow 1: Explore Map
1. User lands on map view (home page)
2. Map shows clustered pins for artists/events across Ukraine
3. User zooms/pans or filters by region/type/style/tags
4. Pins update in real-time
5. User clicks a cluster → zooms to expand
6. User clicks individual pin → preview card appears (drawer/sheet)
7. Preview shows: name, style, image, audio preview (if available)
8. User clicks "View Profile" → navigates to `/artists/:slug`

### Flow 2: Browse Artists
1. User navigates to `/artists`
2. Sees grid of artist cards (name, image, region, tags)
3. Applies filters: region, style, tags
4. Artists list updates
5. User clicks artist card → navigates to `/artists/:slug`
6. Artist profile shows: hero image, bio, press release, gallery, tracks, social links
7. User plays demo track → mini-player appears
8. User shares artist link (social share buttons)

### Flow 3: Discover Events
1. User navigates to `/events`
2. Sees list of upcoming events (cards with image, date, location)
3. Filters by region
4. Clicks event → opens event details (can be modal or `/events/:id`)
5. Event shows: image, description, venue, date/time, ticket link
6. User clicks ticket link → opens external ticketing site

### Flow 4: Submit Artist
1. User navigates to `/submit/artist`
2. Form with sections: Basic Info, Biography, Media, Members, Social Links
3. User fills required fields:
   - Name, style, region, city, coordinates (map picker)
   - Biography, press release
   - Upload images (gallery)
   - Upload demo tracks (audio files)
   - Add band members (if applicable)
   - Add social media links
4. Form validates in real-time (Zod schema)
5. User submits → multipart upload with progress bar
6. Success message appears with info about approval process
7. User receives confirmation (optional: email notification)

### Flow 5: Listen to Radio
1. User navigates to `/radio`
2. Selects region (optional filter)
3. Playlist loads with tracks from regional artists
4. User clicks play → audio starts
5. Global mini-player appears (persists across page navigation)
6. Mini-player shows: track title, artist name, play/pause, progress, skip
7. Clicking artist name navigates to their profile

---

## COMPONENT ARCHITECTURE

### Folder Structure (Recommended)

```
frontend/
├── public/
│   ├── favicon.ico
│   └── ukraine-map.svg
├── src/
│   ├── app/                      # App shell
│   │   ├── App.tsx
│   │   ├── router.tsx
│   │   └── providers.tsx
│   ├── pages/                    # Route pages
│   │   ├── MapPage.tsx
│   │   ├── ArtistsListPage.tsx
│   │   ├── ArtistProfilePage.tsx
│   │   ├── EventsPage.tsx
│   │   ├── NewsPage.tsx
│   │   ├── RadioPage.tsx
│   │   ├── SubmitArtistPage.tsx
│   │   └── SubmitEventPage.tsx
│   ├── features/                 # Feature modules
│   │   ├── map/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   └── utils/
│   │   ├── artists/
│   │   ├── events/
│   │   ├── radio/
│   │   ├── news/
│   │   └── submit/
│   ├── components/               # Shared UI components
│   │   ├── ui/                   # shadcn/ui components
│   │   ├── layout/
│   │   │   ├── Header.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── MobileNav.tsx
│   │   ├── common/
│   │   │   ├── Card.tsx
│   │   │   ├── Button.tsx
│   │   │   └── Spinner.tsx
│   │   └── player/
│   │       └── MiniPlayer.tsx
│   ├── lib/                      # Core utilities
│   │   ├── api/
│   │   │   ├── client.ts
│   │   │   ├── generated/        # Generated types from swagger.json
│   │   │   └── hooks/            # TanStack Query hooks
│   │   ├── config.ts
│   │   ├── constants.ts
│   │   └── utils.ts
│   ├── stores/                   # Zustand stores
│   │   ├── usePlayerStore.ts
│   │   └── useFilterStore.ts
│   ├── styles/
│   │   └── globals.css
│   ├── assets/
│   │   ├── images/
│   │   └── icons/
│   ├── types/
│   │   └── index.ts
│   └── main.tsx
├── .env.example
├── .env.local
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
├── .eslintrc.json
└── .prettierrc
```

### Key Components to Build

**Map Feature**
- `<MapContainer>` - Map wrapper with MapLibre GL
- `<MapPins>` - Render pins with clustering
- `<MapFilters>` - Filter panel (region, type, style, tags)
- `<PinPreviewDrawer>` - Preview card for selected pin

**Artists Feature**
- `<ArtistCard>` - Grid item for artists list
- `<ArtistProfile>` - Full profile layout
- `<ArtistGallery>` - Image carousel/grid
- `<TracksList>` - Audio tracks with player

**Events Feature**
- `<EventCard>` - Event list item
- `<EventDetails>` - Full event info

**Radio Feature**
- `<RadioPlayer>` - Full-page playlist player
- `<TrackItem>` - Single track in playlist

**Submit Feature**
- `<ArtistSubmitForm>` - Multi-step form
- `<EventSubmitForm>` - Event submission form
- `<FileUpload>` - Drag & drop upload with preview

**Shared Components**
- `<Header>` - Top navigation
- `<MobileNav>` - Bottom tab bar
- `<MiniPlayer>` - Persistent audio player
- `<RegionSelect>` - Region dropdown
- `<ErrorBoundary>` - Error handling
- `<LoadingSpinner>` - Loading states
- `<EmptyState>` - No results placeholder

---

## YOUR TASK: GENERATE STEP-BY-STEP IMPLEMENTATION PLAN

**Output Format Requirements**:

Each step must follow this structure:

```
**Step N — Goal Description**

**Commands:**
```bash
# Exact command to run
npm install package-name
```

**Files to Create/Modify:**
- `path/to/file.tsx`
- `path/to/another-file.ts`

**Implementation Details:**
- Specific bullet point 1
- Specific bullet point 2
- Code snippet if needed (small, focused)

**Acceptance Check:**
- How to verify this step works
- Expected behavior or output
- UI state to check
```

**Requirements**:
1. **Be Concrete** - No vague statements like "add some styling"
2. **Be Sequential** - Each step builds on previous ones
3. **Include Commands** - Exact install commands, no guessing
4. **Specify Files** - Full relative paths from project root
5. **Provide Acceptance** - Clear verification criteria
6. **Order by Priority** - MVP features first, then enhancements
7. **Add Stop Points** - Where developer should test/verify before continuing
8. **Justify Choices** - Brief (1-2 lines) rationale for technical decisions

---

## IMPLEMENTATION PHASES (Your Plan Structure)

### Phase A: Project Bootstrap & Foundation (Steps 1-10)
- Initialize Vite React TypeScript project
- Configure Tailwind CSS + PostCSS
- Install and configure shadcn/ui
- Setup React Router with basic routes
- Create base layout (Header, Footer, page container)
- Configure TanStack Query with provider
- Setup error boundary
- Create environment configuration
- Configure ESLint, Prettier, TypeScript strict mode
- Setup path aliases (@/ imports)

### Phase B: API Layer & Type Generation (Steps 11-15)
- Generate TypeScript types from swagger.json
- Create API client with fetch wrapper
- Implement error handling and normalization
- Create TanStack Query hooks for each endpoint
- Add request/response logging in development

### Phase C: Map Explorer - Core Feature (Steps 16-25)
- Install MapLibre GL and react-map-gl
- Create MapContainer component
- Fetch and render pins from /api/v1/map/pins
- Implement pin clustering with supercluster
- Add map controls (zoom, navigation)
- Create MapFilters component (region, type, style, tags)
- Implement filter logic with Zustand store
- Create PinPreviewDrawer component
- Handle pin click → show preview
- Add navigation from preview to profile

### Phase D: Artists Feature (Steps 26-35)
- Create Artists list page with pagination
- Implement ArtistCard component
- Add filter sidebar (region, style, tags)
- Create Artist profile page structure
- Implement artist data fetching by slug
- Build ArtistGallery with image carousel
- Create TracksList with audio player integration
- Add social links section
- Style biography and press release content
- Add SEO meta tags

### Phase E: Events Feature (Steps 36-40)
- Create Events list page
- Implement EventCard component
- Add region filter
- Create event details view (modal or page)
- Add calendar/date formatting

### Phase F: News & Stats (Steps 41-45)
- Create News feed page with pagination
- Implement NewsPostCard component
- Create stats widget component
- Fetch and display platform statistics
- Add loading skeletons

### Phase G: Radio Feature (Steps 46-50)
- Create Radio page with playlist
- Implement Howler.js integration
- Create TrackItem component
- Build MiniPlayer component (global, persistent)
- Implement player controls (play/pause, skip, progress)

### Phase H: Submission Forms (Steps 51-60)
- Create artist submission form (multi-step)
- Implement form validation with Zod schemas
- Add file upload with react-dropzone
- Show upload progress and previews
- Create event submission form
- Add city autocomplete with lookup endpoint
- Implement multipart form data submission
- Show success/error messages with toast
- Add rate limiting or captcha (if needed)

### Phase I: Polish & Optimization (Steps 61-70)
- Add loading skeletons for all data-fetching components
- Create empty states for lists
- Implement retry logic for failed requests
- Add optimistic updates where appropriate
- Configure caching strategies in TanStack Query
- Implement code splitting and lazy loading
- Optimize images (lazy loading, blur placeholders)
- Add SEO meta tags and Open Graph tags
- Create 404 page
- Add basic analytics tracking (optional)

### Phase J: Testing & Quality (Steps 71-75)
- Write unit tests for utility functions
- Write component tests for critical UI
- Create E2E smoke tests with Playwright
- Run Lighthouse audit and fix issues
- Test on mobile devices and different browsers

### Phase K: Deployment Preparation (Steps 76-80)
- Create production build configuration
- Setup environment variable handling
- Document deployment process
- Create docker configuration (if needed)
- Write developer README with setup instructions

---

## QUALITY CHECKLIST (Ensure Every Step Meets These)

Before considering the implementation complete, verify:

- ✅ TypeScript: strict mode, no `any` in production code
- ✅ Accessibility: keyboard navigation works, ARIA labels present
- ✅ Mobile: UI is usable on 360px screen width
- ✅ Performance: Lighthouse score ≥ 90
- ✅ Error Handling: All API calls have error states
- ✅ Loading States: Skeletons or spinners for async operations
- ✅ Empty States: Meaningful messages when no data
- ✅ Form Validation: Real-time feedback, clear error messages
- ✅ Responsive: Layout adapts smoothly from mobile to desktop
- ✅ Browser Support: Works in Chrome, Firefox, Safari, Edge
- ✅ Console: No errors or warnings in production build
- ✅ Code Quality: ESLint and Prettier pass
- ✅ Git: Meaningful commit messages, clean history

---

## CLARIFYING QUESTIONS (Ask if Needed)

Before starting, confirm with the user:

1. **Language**: Should the UI support Ukrainian only, or Ukrainian + English?
2. **Map Provider**: Do you have a MapTiler/Maptiler/OSM token, or should I use free tier?
3. **Branding**: Do you have a logo, specific fonts, or color preferences beyond recommendations?
4. **Deployment**: Where will this be hosted? (Vercel, Netlify, AWS S3+CloudFront, custom server)
5. **Analytics**: Should we integrate Google Analytics, Plausible, or other tracking?
6. **Auth**: Is user authentication planned for future? (Affects state management setup)

---

## FINAL INSTRUCTION

**Now generate the detailed step-by-step implementation plan** following the phase structure above. Make each step actionable, specific, and verifiable.

Begin with Step 1.
