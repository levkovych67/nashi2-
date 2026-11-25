package com.nashi.service;


import com.nashi.dto.NewsPostViewDTO;
import com.nashi.dto.artist.PlatformStatsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MetadataService {
    List<NewsPostViewDTO> getFreshestNews(int limit);
    PlatformStatsDTO getPlatformStats();
    Page<NewsPostViewDTO> getNewsPage(Pageable pageable);
}