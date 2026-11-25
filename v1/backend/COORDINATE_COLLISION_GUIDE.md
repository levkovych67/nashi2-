# Coordinate Collision Avoidance System

## Overview
This system automatically detects and resolves coordinate collisions when multiple artists share the same location (latitude/longitude). When a collision is detected, the new artist's coordinates are offset by a few meters using a spiral pattern, ensuring all artists appear as distinct pins on the map while remaining visually clustered.

## How It Works

### 1. Collision Detection
When a new artist is submitted via `ArtistController.suggestArtist`:
- The system checks for existing artists within **5 meters** of the submitted coordinates
- Uses MongoDB's geospatial queries with 2dsphere index for efficient lookup

### 2. Coordinate Adjustment
If a collision is detected:
- **First artist**: Keeps original coordinates (no adjustment)
- **Subsequent artists**: Offset using a spiral pattern around the original point

### 3. Deterministic Spiral Search Algorithm
The algorithm systematically searches for collision-free positions in expanding rings:
- **Minimum spacing**: 25 meters between all map pins (ensures visual clarity on mobile)
- **Starting radius**: 30 meters from original point
- **Ring pattern**: 8 positions per ring (45° apart - N, NE, E, SE, S, SW, W, NW)
- **Expanding search**: Each ring expands by 15 meters
- **Maximum radius**: 200 meters search area
- **Strict verification**: Each position verified to be ≥25m from ALL existing artists
- **Deterministic**: Same input always produces same output (no randomness)
- **Guaranteed spacing**: Never returns overlapping coordinates

### Visual Result
```
Original Point (49.8397, 24.0297)
    ↓
    ●  ← Artist 1 (original coordinates)
   
   ● ● ← Artists 2-7 (12m radius, 60° apart)
  ●   ●
   ● ●
   
  ● ● ● ← Artists 8+ (expanding spiral, 45° apart)
 ●     ●
  ● ● ●
```

## Implementation Details

### Key Components

#### 1. `GeoCoordinateService` Interface
Location: `src/main/java/com/nashi/service/GeoCoordinateService.java`

Methods:
- `adjustCoordinatesIfCollision(latitude, longitude)` - Main collision detection and adjustment
- `hasCoordinateCollision(latitude, longitude)` - Check if coordinates are occupied

#### 2. `GeoCoordinateServiceImpl` Implementation
Location: `src/main/java/com/nashi/service/impl/GeoCoordinateServiceImpl.java`

Key constants:
```java
private static final double BASE_OFFSET_METERS = 12.0;  // Base offset distance
private static final double COLLISION_DETECTION_RADIUS_METERS = 5.0;  // Collision threshold
```

#### 3. Integration in `ArtistServiceImpl`
Location: `src/main/java/com/nashi/service/impl/artist/ArtistServiceImpl.java`

In the `createSuggestion` method:
```java
// Check for coordinate collision and adjust if necessary
GeoJsonPoint adjustedLocation = geoCoordinateService.adjustCoordinatesIfCollision(
    dto.getLatitude(), 
    dto.getLongitude()
);

Artist artist = Artist.builder()
    // ... other fields
    .location(adjustedLocation)  // Use adjusted coordinates
    .build();
```

## Configuration

### Adjustable Parameters

You can modify these constants in `GeoCoordinateServiceImpl`:

```java
// Distance between offset pins (in meters)
private static final double BASE_OFFSET_METERS = 12.0;

// Collision detection radius (in meters) 
private static final double COLLISION_DETECTION_RADIUS_METERS = 5.0;
```

**Recommendations:**
- **Urban areas**: 8-15 meters works well for city locations
- **Rural areas**: Can increase to 20-30 meters for wider spacing
- **Collision radius**: Keep at 5 meters to detect exact coordinate matches

## Deterministic Spiral Search Flow

### How the Algorithm Finds a Free Position

```
Step 0: Check original coordinates [49.8397, 24.0297]
  → Too close to existing artist (15m away, need 25m minimum)
  → Start spiral search...

Ring 1 (30m radius): Try 8 positions (0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°)
  Position 1 (0° North):  [49.8400, 24.0297] → Collision (12m from artist)
  Position 2 (45° NE):    [49.8399, 24.0300] → Collision (18m from artist)
  Position 3 (90° East):  [49.8397, 24.0301] → Safe! ✓ (28m from all artists)
  → Found free position after 3 attempts

Ring 2 (45m radius): Only checked if Ring 1 failed
Ring 3 (60m radius): Only checked if Ring 2 failed
...
Ring 12 (200m radius): Maximum search radius
```

**Key Benefits:**
- **100% collision-free**: Each position verified to be ≥25m from ALL artists
- **Deterministic**: Same input always produces same output
- **Efficient**: Single database query, then in-memory checking
- **Handles any density**: Searches up to 200m radius (96 positions total)
- **Visual clarity**: 25m minimum spacing ensures pins don't overlap on mobile

## Examples

### Example 1: Single Location, Multiple Artists

**Scenario**: 3 bands from "Atlas, Lviv" submit with coordinates `49.8397, 24.0297`

**Result**:
- **Artist 1**: `49.8397, 24.0297` (original)
- **Artist 2**: `49.83981, 24.02970` (~12m north) - attempt 1
- **Artist 3**: `49.83964, 24.03080` (~12m at 120°) - attempt 2

On the map, users will see 3 distinct pins in a small cluster at Atlas.

### Example 2: Dense Cluster (10 Artists)

**Scenario**: 10 artists submit from same venue

**Result**: Algorithm tries positions in spiral pattern until finding free spots:
- Artists 1-6: Hexagonal pattern (60° apart)
- Artists 7-10: Expanding spiral (45° apart, increased radius)
- Each position verified to be collision-free before assignment

### Example 3: No Collision

**Scenario**: Artist submits coordinates for a unique location

**Result**: Original coordinates used, no offset applied (attempt 0 succeeds)

## Logging

The system logs coordinate adjustments:

```
INFO  - Coordinate collision detected at [49.8397, 24.0297]. 2 existing artists nearby. Applying offset...
INFO  - Adjusted coordinates from [49.8397, 24.0297] to [49.83981, 24.02970]
```

## Database Requirements

### Automatic Index Creation

The application automatically creates the required geospatial index on startup via `MongoIndexConfig`:

```java
@Configuration
public class MongoIndexConfig {
    @Bean
    public CommandLineRunner ensureIndexes() {
        // Creates 2dsphere index on artists.location field
    }
}
```

**On first startup**, the application will:
1. Connect to MongoDB
2. Create the `location_2dsphere` index on the `artists` collection
3. Log: `"Successfully ensured geospatial index on artists.location field"`

### Manual Index Creation (Optional)

If needed, you can also create the index manually via MongoDB shell:

```javascript
db.artists.createIndex({ location: "2dsphere" })
```

### Verify Index Exists

Check if the index was created:

```javascript
db.artists.getIndexes()
// Should show: { "key": { "location": "2dsphere" }, "name": "location_2dsphere" }
```

### Troubleshooting

If you see the error: `"unable to find index for $geoNear query"`
1. **Restart the application** - Index will be created automatically
2. **Check MongoDB logs** - Ensure write permissions
3. **Manual creation** - Run the manual index creation command above

## Frontend Considerations

### Map Display
- Offset distances (10-15m) are visible at zoom levels 16+ (street view)
- At city/region zoom levels, pins will appear clustered
- Consider using marker clustering for areas with many artists

### User Experience
- Users don't need to know about coordinate adjustment
- All artists appear near their actual location
- No manual coordinate adjustment required

## Testing

### Manual Testing

1. **Test collision detection**:
   ```bash
   # Submit first artist with coordinates
   curl -X POST http://localhost:8080/api/v1/artists/suggest \
     -F "data={\"latitude\": 49.8397, \"longitude\": 24.0297, ...}"
   
   # Submit second artist with same coordinates
   # Should be automatically offset
   ```

2. **Verify on map**:
   - Open frontend map view
   - Zoom to street level
   - Observe distinct pins for artists at same venue

### Database Verification

```javascript
// Check artists at similar coordinates
db.artists.find({
  location: {
    $near: {
      $geometry: {
        type: "Point",
        coordinates: [24.0297, 49.8397]  // [longitude, latitude]
      },
      $maxDistance: 20  // 20 meters
    }
  }
})
```

## Benefits

✅ **Automatic handling** - No manual coordinate adjustment needed
✅ **Visually distinct** - Each artist has unique map pin
✅ **Logical clustering** - Artists at same venue appear grouped
✅ **Scalable** - Supports unlimited artists at one location
✅ **Transparent** - Original location data preserved in city/venue fields

## Limitations

⚠️ **Very high density**: If 50+ artists at exact same spot, pins may overlap at street zoom
⚠️ **Retroactive**: Only applies to new submissions (existing data not affected)
⚠️ **Geospatial dependency**: Requires MongoDB with geospatial index

## Future Enhancements

Consider adding:
1. **Configuration via application.properties** for offset distances
2. **Batch processing** script to fix existing duplicate coordinates
3. **Admin override** to manually adjust coordinates
4. **Venue-based grouping** for smarter clustering
