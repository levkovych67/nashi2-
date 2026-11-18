package com.nashi.config;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/config")
public class PublicConfigController {

    private final PublicConfig config;

    public PublicConfigController(NashiProperties p) {
        this.config = new PublicConfig(p.audio().fragmentSeconds(),
                new PublicConfig.MapConfig(p.map().regionMaxZoom(), p.map().placeMaxZoom(), p.map().minZoom(),
                        p.map().maxZoom()),
                new PublicConfig.LikesConfig(p.likes().batchMax(), p.likes().flushMs()),
                new PublicConfig.SubmissionConfig(p.submission().maxImages(), p.submission().maxImageMb(),
                        p.submission().summaryMin(), p.submission().summaryMax()),
                null);
    }

    @Operation(operationId = "getPublicConfig", summary = "Публічний конфіг клієнта")
    @GetMapping("/public")
    public PublicConfig publicConfig() {
        return config;
    }
}
