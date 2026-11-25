# Error Handling Mechanism - Implementation Guide

## Overview
This document describes the comprehensive error handling mechanism implemented for the Nashi backend to properly return errors to the frontend.

## Architecture

### 1. Custom Exception Classes
Located in `src/main/java/com/nashi/api/exception/`

#### BusinessException (Base Class)
- Base exception for all business logic errors
- Contains error code and HTTP status
- All custom exceptions extend this class

#### Specific Exception Types

**ResourceNotFoundException**
- HTTP Status: 404 NOT FOUND
- Use when: A requested resource (Artist, Event, etc.) is not found
- Example:
```java
throw new ResourceNotFoundException("Artist", artistId);
// Returns: "Artist not found with identifier: 12345"
```

**ValidationException**
- HTTP Status: 400 BAD REQUEST
- Use when: Custom validation fails
- Example:
```java
throw new ValidationException("Disband year cannot be earlier than foundation year");
```

**ExternalServiceException**
- HTTP Status: 503 SERVICE UNAVAILABLE
- Use when: External service calls fail (AI, S3, Telegram)
- Example:
```java
throw new ExternalServiceException("AI", "Failed to parse coordinates", exception);
```

**FileUploadException**
- HTTP Status: 400 BAD REQUEST
- Use when: File upload or processing fails
- Example:
```java
throw new FileUploadException("Failed to upload event image", exception);
```

**MaxUploadSizeExceededException** (Spring built-in)
- HTTP Status: 413 PAYLOAD TOO LARGE
- Automatically caught when file upload size exceeds configured maximum
- Returns human-readable file size information

### 2. Global Exception Handler
Located in `src/main/java/com/nashi/api/GlobalExceptionHandler.java`

Automatically catches all exceptions and formats them into consistent API error responses.

### 3. API Error Response Format

All errors are returned in this standardized format:

```json
{
  "timestamp": "2025-12-24T14:02:00+02:00",
  "status": 404,
  "error": "Not Found",
  "message": "Artist not found with identifier: abc123",
  "path": "/api/v1/artists/abc123",
  "details": {
    "errorCode": "RESOURCE_NOT_FOUND"
  }
}
```

## Implementation Examples

### EventService
**Before:**
```java
throw new RuntimeException("Error parsing AI response", e);
```

**After:**
```java
throw new ExternalServiceException("AI", "Failed to parse coordinates from AI service", e);
```

### ArtistService
**Before:**
```java
throw new IllegalArgumentException("Disband year cannot be earlier than foundation year");
```

**After:**
```java
throw new ValidationException("Disband year cannot be earlier than foundation year");
```

## Error Codes
Current error codes available:

- `RESOURCE_NOT_FOUND` - Resource not found (404)
- `VALIDATION_ERROR` - Validation failed (400)
- `EXTERNAL_SERVICE_ERROR` - External service unavailable (503)
- `FILE_UPLOAD_ERROR` - File upload failed (400)

## How to Add New Custom Exceptions

1. Create new exception class extending `BusinessException`
2. Set appropriate error code and HTTP status
3. Add handler in `GlobalExceptionHandler` (optional, as `BusinessException` handler catches all)

Example:
```java
public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String resourceType) {
        super(
            "DUPLICATE_RESOURCE",
            String.format("%s already exists", resourceType),
            HttpStatus.CONFLICT
        );
    }
}
```

## Frontend Integration

The frontend can now:

1. **Display user-friendly error messages** from the `message` field
2. **Handle errors by status code** (400, 404, 503, etc.)
3. **Implement specific logic based on error codes** from `details.errorCode`
4. **Show validation errors** from `details.fieldErrors` (for form validation)

Example frontend error handling:
```typescript
try {
  const response = await api.createEvent(data);
} catch (error) {
  if (error.status === 503) {
    // External service error - maybe retry
    showError(error.message);
  } else if (error.status === 400) {
    // Validation error - show field errors
    showValidationErrors(error.details.fieldErrors);
  } else if (error.status === 404) {
    // Not found - redirect to 404 page
    redirectTo404();
  }
}
```

## Benefits

✅ **Consistent error format** across all endpoints
✅ **Proper HTTP status codes** for different error types
✅ **Meaningful error messages** for frontend display
✅ **Error codes** for programmatic error handling
✅ **Transaction rollback** on critical errors
✅ **Better logging** with error context
✅ **Type safety** with custom exception classes

## Testing

To test error responses, make requests that trigger errors:

1. **404 Error**: Request non-existent artist
   ```bash
   curl http://localhost:8080/api/v1/artists/invalid-id
   ```

2. **400 Validation Error**: Send invalid data
   ```bash
   curl -X POST http://localhost:8080/api/v1/artists/suggest \
     -F "data={\"disbandYear\": 2000, \"foundationYear\": 2010}"
   ```

3. **503 Service Error**: Trigger when AI service is down or fails

## Next Steps

Consider adding:
- Rate limiting exceptions
- Authentication/Authorization exceptions
- Database connection exceptions
- Custom business rule exceptions specific to your domain
