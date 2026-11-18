package com.nashi.wiki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Who made a photo and under which license, as Wikimedia Commons states it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WikiImageCredit {
    private String author;
    private String license;
    private String licenseUrl;
    /** The file's page on Wikimedia Commons. */
    private String filePageUrl;
}
