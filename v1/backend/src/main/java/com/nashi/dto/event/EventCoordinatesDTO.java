package com.nashi.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventCoordinatesDTO {
    private Double latitude;
    private Double longitude;
}