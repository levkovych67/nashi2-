package com.nashi.service;

 import com.nashi.dto.TrackDTO;
 import com.nashi.enums.Region;
import java.util.List;

public interface RadioService {
    List<TrackDTO> generateMix(Region region);
}