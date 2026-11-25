package com.nashi.controller;

import com.nashi.service.MetadataService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataControllerTest {

    private final MetadataService metadataService = mock(MetadataService.class);
    private final MetadataController controller = new MetadataController(metadataService);

    @Test
    void limitsAreCappedAt50() {
        when(metadataService.getFreshestNews(anyInt())).thenReturn(List.of());
        when(metadataService.getNewsPage(any())).thenReturn(Page.empty());

        controller.getLatestNews(10_000);
        controller.getLatestNews(7);
        controller.getLatestNews(0);
        controller.getLatestNews(3, 10_000);
        controller.getLatestNews(-1, 0);

        verify(metadataService).getFreshestNews(50);
        verify(metadataService).getFreshestNews(7);
        verify(metadataService).getFreshestNews(1);
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(metadataService, times(2)).getNewsPage(pages.capture());
        assertEquals(3, pages.getAllValues().get(0).getPageNumber());
        assertEquals(50, pages.getAllValues().get(0).getPageSize());
        assertEquals(0, pages.getAllValues().get(1).getPageNumber());
        assertEquals(1, pages.getAllValues().get(1).getPageSize());
    }
}
