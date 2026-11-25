package com.nashi.service.impl;

import com.nashi.service.DataValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataValidationServiceImpl implements DataValidationService {

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/jpg"
    );

    private static final List<String> ALLOWED_AUDIO_TYPES = List.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/flac", "audio/mp4","audio/wave"
    );

    @Override
    public List<MultipartFile> validateImages(List<MultipartFile> files) {
        return filterFiles(files, ALLOWED_IMAGE_TYPES, "Image");
    }

    @Override
    public List<MultipartFile> validateAudio(List<MultipartFile> files) {
        return filterFiles(files, ALLOWED_AUDIO_TYPES, "Audio");
    }

    /**
     * Універсальний метод фільтрації
     */
    private List<MultipartFile> filterFiles(List<MultipartFile> files, List<String> allowedTypes, String logType) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>(); // Повертаємо пустий список, щоб уникнути NullPointerException далі
        }

        return files.stream()
                .filter(file -> {
                    // 1. Відкидаємо пусті файли
                    if (file.isEmpty()) return false;

                    String contentType = file.getContentType();
                    // 2. Перевіряємо тип
                    boolean isValid = contentType != null && allowedTypes.contains(contentType.toLowerCase());

                    if (!isValid) {
                        log.warn("Filtered out invalid {} file. Name: {}, Type: {}",
                                logType, file.getOriginalFilename(), contentType);
                    }

                    return isValid;
                })
                .collect(Collectors.toList());
    }
}