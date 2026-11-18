package com.nashi.musician;

import java.util.List;

/** {@code MusicianPage} of the contract. */
public record MusicianPage(List<MusicianListItem> items, int page, int size, long totalItems, int totalPages) {
}
