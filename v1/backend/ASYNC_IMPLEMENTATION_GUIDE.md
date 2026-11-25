# Async Implementation Guide - Non-Blocking Artist & Event Submissions

## Overview
The artist and event suggestion endpoints now process submissions asynchronously. The frontend receives an immediate response (202 ACCEPTED) while processing (AI calls, file uploads, Telegram notifications) continues in the background.

## Why Async?

### Problem Before
When a user submitted an artist or event:
1. Frontend sends request → **waits**
2. Backend validates data → **frontend still waiting**
3. Backend calls AI for tags/coordinates → **frontend still waiting** (can take 5-10 seconds)
4. Backend uploads images to S3 → **frontend still waiting**
5. Backend sends Telegram notification → **frontend still waiting**
6. Finally returns response → **total wait: 15-30 seconds**

### Solution Now
When a user submits an artist or event:
1. Frontend sends request
2. Backend immediately returns **202 ACCEPTED** → **frontend gets response in <100ms**
3. Backend processes everything in background thread
4. User can continue using the app immediately

## Implementation

### 1. Async Configuration (`AsyncConfig.java`)

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 5 threads always running
        executor.setMaxPoolSize(10);        // Can scale up to 10 threads
        executor.setQueueCapacity(100);     // Queue up to 100 tasks
        executor.setThreadNamePrefix("async-suggestion-");
        return executor;
    }
}
```

**Thread Pool Settings:**
- **Core Pool Size (5)**: Always-ready threads for immediate task execution
- **Max Pool Size (10)**: Can scale up during high load
- **Queue Capacity (100)**: Tasks wait in queue if all threads busy

### 2. Service Layer

#### Artist Service
```java
// Synchronous method (original)
@Transactional
public Artist createSuggestion(ArtistCreateRequestDTO dto, 
                              List<MultipartFile> images,
                              List<MultipartFile> demoTracks) {
    // Process everything synchronously
}

// NEW: Async wrapper method
@Async
@Transactional
public void createSuggestionAsync(ArtistCreateRequestDTO dto,
                                  List<MultipartFile> images,
                                  List<MultipartFile> demoTracks) {
    log.info("[ASYNC] Processing suggestion for artist: {}", dto.getName());
    try {
        createSuggestion(dto, images, demoTracks);
        log.info("[ASYNC] Successfully processed suggestion");
    } catch (Exception e) {
        log.error("[ASYNC] Failed to process suggestion", e);
        // Errors logged but don't propagate to controller
    }
}
```

#### Event Service
```java
// Synchronous method (original)
@Transactional
public void suggestEvent(EventCreateRequestDTO request, MultipartFile image) {
    // Process everything synchronously
}

// NEW: Async wrapper method
@Async
@Transactional
public void suggestEventAsync(EventCreateRequestDTO request, MultipartFile image) {
    log.info("[ASYNC] Processing event suggestion: {}", request.getTitle());
    try {
        suggestEvent(request, image);
        log.info("[ASYNC] Successfully processed event");
    } catch (Exception e) {
        log.error("[ASYNC] Failed to process event", e);
        // Errors logged but don't propagate to controller
    }
}
```

### 3. Controller Layer

#### Artist Controller
**Before:**
```java
@PostMapping
public ResponseEntity<String> suggestArtist(...) {
    Artist suggestion = artistService.createSuggestion(dto, images, tracks);
    telegramService.sendArtistForApproval(suggestion);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body("Artist suggestion submitted successfully.");
}
```

**After:**
```java
@PostMapping
public ResponseEntity<String> suggestArtist(...) {
    // Returns immediately, processes in background
    artistService.createSuggestionAsync(dto, images, tracks);
    
    return ResponseEntity.status(HttpStatus.ACCEPTED)  // 202 instead of 201
            .body("Artist suggestion submitted successfully. It is being processed and will be reviewed shortly.");
}
```

#### Event Controller
**Before:**
```java
@PostMapping("/suggest")
public ResponseEntity<Void> suggestEvent(...) {
    eventService.suggestEvent(request, image);
    return ResponseEntity.ok().build();
}
```

**After:**
```java
@PostMapping("/suggest")
public ResponseEntity<String> suggestEvent(...) {
    // Returns immediately, processes in background
    eventService.suggestEventAsync(request, image);
    
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body("Event suggestion submitted successfully. It is being processed and will be reviewed shortly.");
}
```

## HTTP Status Codes

| Status | Code | When Used |
|--------|------|-----------|
| CREATED | 201 | Synchronous - resource created immediately |
| **ACCEPTED** | **202** | **Async - request accepted, processing in background** |

## Frontend Integration

### Before (Synchronous)
```typescript
async function submitArtist(data) {
  setLoading(true);
  try {
    const response = await api.post('/artists', data);
    // Waits 15-30 seconds here...
    showSuccess("Artist submitted!");
  } catch (error) {
    showError(error.message);
  } finally {
    setLoading(false);
  }
}
```

### After (Async)
```typescript
async function submitArtist(data) {
  setLoading(true);
  try {
    const response = await api.post('/artists', data);
    // Gets response in <100ms
    
    if (response.status === 202) {
      showSuccess("Artist submitted! We're processing it now.");
      // Optionally redirect or show next steps
      router.push('/artists');
    }
  } catch (error) {
    // Only validation errors are caught here
    showError(error.message);
  } finally {
    setLoading(false);
  }
}
```

## Error Handling

### Validation Errors (Immediate)
These errors return immediately before async processing:
- Missing required fields
- Invalid file formats
- File size too large
- Invalid data types

**Response:** 400 BAD REQUEST

### Processing Errors (Background)
These errors occur during async processing:
- AI service failures
- S3 upload failures
- Database errors
- Telegram notification failures

**Behavior:** 
- Logged to server logs with `[ASYNC]` prefix
- Admin receives notification via Telegram (if configured)
- User receives the 202 ACCEPTED response (doesn't see the error)

## Monitoring & Logging

### Log Patterns

**Successful Processing:**
```
INFO  - Received suggestion for artist: DakhaBrakha
INFO  - [ASYNC] Processing suggestion for artist: DakhaBrakha
INFO  - Processing suggestion for artist: DakhaBrakha
INFO  - Requesting AI tags for artist: DakhaBrakha
INFO  - AI Tags applied: Music=[folk, world], Vibe=[energetic]
INFO  - Image uploaded successfully. Key: artists/images/abc123.jpg
INFO  - Artist saved successfully with ID: 507f1f77bcf86cd799439011
INFO  - Telegram notification sent for artist ID: 507f1f77bcf86cd799439011
INFO  - [ASYNC] Successfully processed suggestion for artist: DakhaBrakha
```

**Failed Processing:**
```
INFO  - Received suggestion for artist: DakhaBrakha
INFO  - [ASYNC] Processing suggestion for artist: DakhaBrakha
ERROR - AI service error: Connection timeout
ERROR - [ASYNC] Failed to process suggestion for artist: DakhaBrakha
ExternalServiceException: AI service error: Failed to fetch coordinates from AI service
```

### Monitor Async Thread Pool

Check thread pool status in application logs:
```
INFO  - Async executor started with core pool size: 5, max pool size: 10
```

If you see queue capacity warnings, consider increasing:
```java
executor.setQueueCapacity(200); // Increase from 100 to 200
```

## Configuration

### Thread Pool Tuning

Edit `AsyncConfig.java` based on your load:

**Low Traffic (<10 submissions/minute):**
```java
executor.setCorePoolSize(3);
executor.setMaxPoolSize(5);
executor.setQueueCapacity(50);
```

**Medium Traffic (10-50 submissions/minute):**
```java
executor.setCorePoolSize(5);   // Current setting
executor.setMaxPoolSize(10);
executor.setQueueCapacity(100);
```

**High Traffic (>50 submissions/minute):**
```java
executor.setCorePoolSize(10);
executor.setMaxPoolSize(20);
executor.setQueueCapacity(200);
```

## Benefits

✅ **Instant feedback** - Users get response in <100ms instead of 15-30 seconds
✅ **Better UX** - No long waits, can continue using app immediately
✅ **Resilience** - Frontend not affected by backend processing delays
✅ **Scalability** - Can handle more concurrent requests
✅ **Resource efficiency** - Thread pool manages background tasks efficiently

## Limitations & Considerations

⚠️ **No immediate feedback on processing errors** - User won't know if AI/upload fails
⚠️ **Requires monitoring** - Admin must monitor logs for background failures
⚠️ **Transaction boundaries** - Async methods run in separate transactions

### Mitigation Strategies

1. **Status Tracking** (Future Enhancement):
   ```java
   // Add status field to Artist/Event
   enum ProcessingStatus { PENDING, PROCESSING, COMPLETED, FAILED }
   ```

2. **Webhook/Polling** (Future Enhancement):
   - Frontend polls `/api/v1/artists/{id}/status`
   - Or use WebSockets for real-time updates

3. **Email Notifications**:
   - Send email when processing completes
   - Include success/failure status

## Testing

### Test Async Behavior

1. **Submit artist/event:**
   ```bash
   time curl -X POST http://localhost:8080/api/v1/artists \
     -F "data={...}" \
     -F "images=@photo.jpg"
   ```
   
   Should return in <1 second with 202 ACCEPTED

2. **Check logs:**
   ```bash
   tail -f logs/application.log | grep "ASYNC"
   ```

3. **Verify processing:**
   - Check MongoDB for new PENDING artist/event
   - Check S3 for uploaded files
   - Check Telegram for admin notification

### Load Testing

```bash
# Send 10 concurrent requests
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/artists \
    -F "data={...}" \
    -F "images=@photo.jpg" &
done
wait
```

All should return 202 ACCEPTED quickly.

## Troubleshooting

### Issue: Tasks Not Executing
**Symptoms:** 202 response received but nothing processes
**Solution:** Ensure `@EnableAsync` is present in `AsyncConfig`

### Issue: OutOfMemoryError
**Symptoms:** Server crashes under load
**Solution:** Reduce thread pool size or increase heap memory

### Issue: Slow Processing
**Symptoms:** Queue backs up, tasks delayed
**Solution:** Increase core pool size or optimize slow operations (AI calls, uploads)

## Future Enhancements

Consider adding:
1. **Task Queue with Redis** - For distributed async processing
2. **Progress Tracking** - Real-time status updates via WebSocket
3. **Retry Logic** - Automatic retry for failed AI/upload operations
4. **Rate Limiting** - Prevent abuse of async endpoints
5. **Admin Dashboard** - View async task status and failures
