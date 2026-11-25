package com.nashi.controller;


import com.nashi.dto.NewsPostViewDTO;
import com.nashi.dto.artist.PlatformStatsDTO;
import com.nashi.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
public class MetadataController {

    /** Most news posts one request may ask for. */
    static final int MAX_NEWS = 50;

    private final MetadataService metadataService;

    // GET /api/v1/metadata/news/last?limit=3 (limit is kept within 1..50)
    @GetMapping("/news/last")
    public ResponseEntity<List<NewsPostViewDTO>> getLatestNews(
            @RequestParam(defaultValue = "3") int limit
    ) {
        return ResponseEntity.ok(metadataService.getFreshestNews(capped(limit)));
    }

    // GET /api/v1/metadata/news/latest?page=0&size=10 (size is kept within 1..50)
    @GetMapping("/news/latest")
    public ResponseEntity<Page<NewsPostViewDTO>> getLatestNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), capped(size));

        return ResponseEntity.ok(metadataService.getNewsPage(pageable));
    }

    private static int capped(int requested) {
        return Math.min(Math.max(requested, 1), MAX_NEWS);
    }

    // GET /api/v1/metadata/stats
    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsDTO> getStats() {
        return ResponseEntity.ok(metadataService.getPlatformStats());
    }
}