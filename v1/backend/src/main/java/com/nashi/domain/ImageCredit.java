package com.nashi.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Who made a photo and under which license, as the license requires us to show it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCredit {
    private String author;
    private String license;
    private String licenseUrl;
    /** The file's page on Wikimedia Commons. */
    private String filePageUrl;
}
