package com.nashi.controller;

import com.nashi.dto.TrackDTO;
import com.nashi.enums.Region;
import com.nashi.service.RadioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/radio")
@RequiredArgsConstructor
public class RadioController {

    private final RadioService radioService;

    /**
     * Генерує плейлист із 20 випадкових треків.
     * Можна фільтрувати за регіоном.
     * * Приклади:
     * GET /api/v1/radio/mix              -> Мікс по всій Україні
     * GET /api/v1/radio/mix?region=LVIV  -> Тільки львівські гурти
     */
    @GetMapping("/mix")
    public ResponseEntity<List<TrackDTO>> getRadioMix(
            @RequestParam(required = false) Region region
    ) {
        return ResponseEntity.ok(radioService.generateMix(region));
    }
}