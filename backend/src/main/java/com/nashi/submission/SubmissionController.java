package com.nashi.submission;

import com.nashi.ratelimit.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * {@code POST /api/v2/submissions} ({@code docs/02-contracts.md} 2.10): the {@code data} part as JSON and the
 * {@code images} parts. Validation happens in {@link SubmissionService} after the rate limit, not through
 * {@code @Valid}, which would run first.
 */
@RestController
@RequestMapping("/api/v2/submissions")
public class SubmissionController {

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @Operation(operationId = "createSubmission", summary = "Пропозиція музиканта")
    @ApiResponse(responseCode = "202", description = "Заявку прийнято на модерацію")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmissionAccepted create(@RequestPart("data") SubmissionRequest data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            HttpServletRequest request) {
        return service.submit(data, images, ClientIp.of(request));
    }
}
