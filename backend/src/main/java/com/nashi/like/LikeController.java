package com.nashi.like;

import com.nashi.api.FieldViolation;
import com.nashi.api.ValidationFailedException;
import com.nashi.ratelimit.ClientIp;
import com.nashi.ratelimit.RateLimitPolicy;
import com.nashi.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v2/musicians/{id}/likes} ({@code docs/02-contracts.md} 2.8): rate limit first, then validate (so
 * validation is by hand, not {@code @Valid}, which would run before the limiter). */
@RestController
@RequestMapping("/api/v2/musicians")
public class LikeController {

    private final LikeService service;
    private final RateLimiter limiter;
    private final Validator validator;

    public LikeController(LikeService service, RateLimiter limiter, Validator validator) {
        this.service = service;
        this.limiter = limiter;
        this.validator = validator;
    }

    @Operation(operationId = "addLikes", summary = "Додати лайки")
    @PostMapping("/{id}/likes")
    public LikeResponse add(@PathVariable("id") String id, @RequestBody LikeRequest body,
            HttpServletRequest request) {
        limiter.check(RateLimitPolicy.LIKES, ClientIp.of(request));
        var violations = validator.validate(body).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(path -> new FieldViolation(path.toString(), "Некоректне значення"))
                .toList();
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }
        return new LikeResponse(service.add(id, body.count()));
    }
}
