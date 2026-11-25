package com.nashi.service;

 import com.nashi.dto.MapPinDTO;
 import com.nashi.enums.PinType;
 import com.nashi.enums.Region;

import java.util.List;
 import java.util.Set;

public interface MapService {
    /**
     * Отримує список точок для мапи з врахуванням фільтрів.
     * Повертає тільки артистів зі статусом APPROVED.
     */
    List<MapPinDTO> getPins(Region region, String style, List<String> tags);

    List<MapPinDTO> getPins(Region region, String style, List<String> tags, Set<PinType> types);
}