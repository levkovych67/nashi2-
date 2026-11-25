package com.nashi.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DataValidationService {

    // Тепер методи повертають відфільтрований список
    List<MultipartFile> validateImages(List<MultipartFile> files);

    List<MultipartFile> validateAudio(List<MultipartFile> files);
}