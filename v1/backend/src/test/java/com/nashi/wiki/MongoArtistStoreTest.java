package com.nashi.wiki;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoArtistStoreTest {

    @Test
    void knownArtistsIncludeHidden() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(Artist.class))).thenReturn(List.of(
                Artist.builder().name("Прихований").wikidataId("Q111").wikiTitle("Прихований (гурт)")
                        .status(ArtistStatus.HIDDEN).build(),
                Artist.builder().name("Гурт з форми").status(ArtistStatus.HIDDEN).build(),
                Artist.builder().name("Відхилений").status(ArtistStatus.REJECTED).build()));

        KnownArtists known = new MongoArtistStore(mongoTemplate, mock(ArtistRepository.class)).loadKnown();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Artist.class));
        // No criteria at all: every status, HIDDEN included, counts as known.
        assertTrue(query.getValue().getQueryObject().isEmpty());
        assertEquals(1, query.getValue().getFieldsObject().get("status"));

        assertTrue(known.wikidataIds().contains("Q111"));
        assertTrue(known.wikiTitles().contains("Прихований (гурт)"));
        assertTrue(known.manualNames().contains(NameNormalizer.normalize("Гурт з форми")));
        assertFalse(known.manualNames().contains(NameNormalizer.normalize("Відхилений")));
    }
}
