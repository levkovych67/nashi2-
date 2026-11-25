package com.nashi.service.impl;


import com.nashi.domain.NewsPost;
import com.nashi.dto.NewsPostViewDTO;
import com.nashi.dto.artist.PlatformStatsDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import com.nashi.repo.NewsPostRepository;
import com.nashi.service.MetadataService;
import com.nashi.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetadataServiceImpl implements MetadataService {

    private final ArtistRepository artistRepository;
    private final NewsPostRepository newsPostRepository;
    private final S3Service s3Service;

    @Override
    public List<NewsPostViewDTO> getFreshestNews(int limit) {
        // Отримуємо останні N постів
        // Використовуємо PageRequest, щоб не тягнути всю базу
        List<NewsPost> posts = newsPostRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        return posts.stream()
                .map(this::toNewsDto)
                .collect(Collectors.toList());
    }

    @Override
    public PlatformStatsDTO getPlatformStats() {
        long approved = artistRepository.countByStatus(ArtistStatus.APPROVED);
        long pending = artistRepository.countByStatus(ArtistStatus.PENDING);
        
        return PlatformStatsDTO.builder()
                .approvedArtists(approved)
                .pendingArtists(pending)
                .totalArtists(approved + pending)
                .build();
    }

    // --- Helper Mapper ---
    private NewsPostViewDTO toNewsDto(NewsPost post) {
        String imageUrl = (post.getImageKey() != null) ? s3Service.getFileUrl(post.getImageKey()) : null;
        String audioUrl = (post.getAudioKey() != null) ? s3Service.getFileUrl(post.getAudioKey()) : null;

        return NewsPostViewDTO.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(imageUrl)
                .audioUrl(audioUrl)
                .artistName(post.getArtistName())
                .artistSlug(post.getArtistSlug())
                .createdAt(post.getCreatedAt())
                .build();
    }

    @Override
    public Page<NewsPostViewDTO> getNewsPage(Pageable pageable) {
        Page<NewsPost> postPage = newsPostRepository.findAllByOrderByCreatedAtDesc(pageable);
        return postPage.map(this::toNewsDto);
    }
}