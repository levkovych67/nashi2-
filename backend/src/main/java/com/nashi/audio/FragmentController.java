package com.nashi.audio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v2/musicians/{id}/fragment} ({@code docs/02-contracts.md} 2.7). */
@RestController
@RequestMapping("/api/v2/musicians")
public class FragmentController {

    private final FragmentService service;

    public FragmentController(FragmentService service) {
        this.service = service;
    }

    /** Never cached: the answer is random and a Deezer URL expires. */
    @Operation(operationId = "getFragment", summary = "Випадковий фрагмент аудіо")
    @ApiResponse(responseCode = "200", description = "Фрагмент",
            content = @Content(schema = @Schema(implementation = Fragment.class)))
    @ApiResponse(responseCode = "204", description = "Аудіо немає")
    @GetMapping("/{id}/fragment")
    public ResponseEntity<Fragment> fragment(@PathVariable("id") String id,
                                             @RequestParam(name = "exclude", required = false) String exclude) {
        return service.fragment(id, exclude)
                .map(f -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(f))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
}
