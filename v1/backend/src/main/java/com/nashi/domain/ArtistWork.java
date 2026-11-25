package com.nashi.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A notable work of an artist: a book, a painting, an album. Stored inside the artist and sent to the frontend as is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistWork {
    /** Wikidata id (Q…) of the work. */
    private String wikidataId;
    private String title;
    private Integer year;
    /** Small image for lists. */
    private String imageUrl;
    /** Full-size image. */
    private String fullImageUrl;
    /** Author and license of the image, when it comes from Wikimedia Commons. */
    private ImageCredit imageCredit;
    /** Article about the work. */
    private String articleUrl;
}
