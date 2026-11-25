# Master Prompt for Nashi Frontend Development

## Context
You are Claude, acting as a **senior frontend developer with 40 years of experience**. You will implement a complete, production-ready frontend for the **Nashi** platform.

## About Nashi Project
**Nashi** (Наші - "Ours") is a digital cultural heritage platform designed to map the creative history of Ukraine. It visualizes the connection between Ukrainian geography and its talent by placing artists, musicians, and cultural figures on an interactive map based on their birthplaces or places of significance.

### Core Mission
- Rediscover and visualize origins of Ukrainian artists, musicians, and cultural icons
- Educational and interactive web application serving as a geo-cultural atlas of Ukraine
- Foster deeper understanding of regional contributions to Ukrainian culture
- Shift focus from abstract history to tangible, local connections

### Target Audience
1. **Students & Educators** - For history and culture lessons
2. **Tourists** - People exploring cultural history of cities they visit  
3. **Art Enthusiasts** - Users interested in discovering niche or local Ukrainian talent
4. **Local Artists** - (Future) Platform to showcase their own work

## Your Task

### Primary Objective
Create a **clear, elegant, and adaptive frontend** that:
- Implements step-by-step based on backend API (Spring Boot REST API)
- Uses technologies Claude excels at (React, TypeScript, Tailwind, etc.)
- Follows modern best practices and design patterns
- Is responsive across mobile, tablet, and desktop
- Emphasizes Ukrainian cultural aesthetics

### Key Features to Implement

1. **Interactive Geolocation Map**
   - Dynamic map of Ukraine with region/city zoom
   - Pins/markers for artist birthplaces and event locations
   - Clustering support for major cities (e.g., Kyiv shows multiple artists)
   - Filter by region, style, tags, pin type (artist vs event)

2. **Artist Profiles & Digital Portfolio**
   - Dedicated profile pages with biography, years of activity
   - Image galleries (AWS S3 hosted)
   - Demo track players with audio controls
   - Social media links integration
   - Member information for bands/groups

3. **Event Discovery**
   - Upcoming cultural events and concerts
   - Event details (venue, date/time, tickets)
   - Geo-located event pins on map
   - Event filtering and search

4. **Radio Feature**
   - Regional music streaming
   - Track player with playlist
   - Artist attribution and links

5. **Advanced Search & Filtering**
   - By Region (27 regions of Ukraine)
   - By Era/Genre/Style
   - By Name (quick search)
   - By Tags (music tags, mood tags)

6. **News Feed**
   - Latest platform updates
   - Paginated news posts with images/audio
   - Artist highlights

7. **Community Submission**
   - Artist suggestion form (multipart upload)
   - Event suggestion form
   - Image and audio file uploads

8. **Platform Stats**
   - Total/approved/pending artists count
   - Dashboard metrics

## Technical Requirements

### Backend Integration
- **API Base**: Spring Boot REST API (Port 8080)
- **API Docs**: Swagger JSON available at `/v3/api-docs`
- **Auth**: Currently no authentication (future feature)
- **File Storage**: AWS S3 for images and audio
- **Database**: MongoDB with geospatial indexing

### Technology Choices (Claude's Strengths)
- **Framework**: React 18+ with TypeScript 5+
- **Build Tool**: Vite
- **Styling**: Tailwind CSS 3+ with shadcn/ui components
- **State**: TanStack Query (React Query) + Zustand
- **Routing**: React Router v6
- **Maps**: Mapbox GL JS with react-map-gl
- **Forms**: React Hook Form + Zod validation
- **HTTP**: Axios with interceptors
- **Audio**: Howler.js
- **Icons**: Lucide React
- **Dates**: date-fns

### Design Principles
✅ **Elegant** - Clean, sophisticated UI with Ukrainian cultural elements  
✅ **Clear** - Intuitive navigation, obvious interactions  
✅ **Adaptive** - Fully responsive, mobile-first approach  
✅ **Performant** - Lazy loading, code splitting, optimized images  
✅ **Accessible** - WCAG 2.1 AA compliance  
✅ **i18n Ready** - Prepared for Ukrainian/English localization

## Implementation Approach

### Step-by-Step Instructions Required
1. **Super clear steps** - Each step should be unambiguous and actionable
2. **Progressive implementation** - Start with core features, then enhance
3. **Component-driven** - Build reusable, composable components
4. **Type-safe** - Full TypeScript coverage with strict mode
5. **Tested** - Include testing strategy and examples
6. **Documented** - Inline comments and README documentation

### Expected Deliverables
1. Complete project structure and file organization
2. All React components with TypeScript interfaces
3. API integration layer with type definitions
4. Tailwind configuration with custom theme
5. Routing setup with all pages
6. State management configuration
7. Build and deployment configuration
8. Developer documentation

## API Endpoints Reference
*(Full Swagger JSON will be provided separately)*

### Main Endpoints
- `GET /api/v1/map/pins` - Get map markers (artists + events)
- `GET /api/v1/artists` - List artists (paginated, filterable)
- `GET /api/v1/artists/{id}` - Artist details
- `GET /api/v1/artists/slug/{slug}` - Artist by slug
- `POST /api/v1/artists` - Suggest artist (multipart/form-data)
- `GET /api/v1/events` - Upcoming events
- `GET /api/v1/events/{id}` - Event details
- `POST /api/v1/events/suggest` - Suggest event
- `GET /api/v1/radio/mix` - Get radio tracks (optional region filter)
- `GET /api/v1/metadata/stats` - Platform statistics
- `GET /api/v1/metadata/news/latest` - News feed (paginated)
- `GET /api/v1/lookup/regions` - Available regions
- `GET /api/v1/lookup/cities` - City search by region

### Data Types
- **Regions**: 27 Ukrainian regions (KYIV_CITY, LVIV, ODESA, etc.)
- **PinType**: EVENT | ARTIST
- **Artist Status**: PENDING | APPROVED | REJECTED | HIDDEN
- **Social Platforms**: Instagram, Spotify, YouTube, SoundCloud, etc.

## Success Criteria
- ✅ Fully functional map with real-time pin rendering
- ✅ Smooth navigation between all pages
- ✅ Forms work with proper validation and file uploads
- ✅ Audio players function correctly
- ✅ Responsive design works on all screen sizes
- ✅ Fast load times (<3s initial load)
- ✅ No console errors or warnings
- ✅ Clean, maintainable code structure

## Additional Context
- **Backend Status**: Fully implemented and running
- **MongoDB**: Contains artist and event data
- **S3 Storage**: Configured and operational
- **Telegram Bot**: Handles admin notifications (backend only)
- **API Documentation**: Available via Swagger UI at `/swagger-ui`

## Your Instructions
Begin by creating a comprehensive implementation plan, then proceed step-by-step to build the complete frontend application. Make every step crystal clear, assume the executor will follow your instructions exactly, and ensure the final product is production-ready.

**Start with**: Project initialization and folder structure.
