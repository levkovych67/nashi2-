package com.nashi.controller;


import com.nashi.dto.event.EventCreateRequestDTO;
import com.nashi.dto.event.EventResponseDTO;
import com.nashi.enums.Region;
import com.nashi.service.impl.event.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;


    @GetMapping
    public ResponseEntity<List<EventResponseDTO>> getUpcomingEvents(
            @RequestParam(required = false) Region region
    ) {
        return ResponseEntity.ok(eventService.getUpcomingEvents(region));
    }



    @PostMapping(value = "/suggest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> suggestEvent(
            @RequestPart("data") @Valid EventCreateRequestDTO request,
            @RequestPart("image") MultipartFile image
    ) {
        eventService.suggestEvent(request, image);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponseDTO> getEventById(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }


}
