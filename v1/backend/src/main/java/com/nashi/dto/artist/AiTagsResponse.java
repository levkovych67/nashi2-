package com.nashi.dto.artist;

import java.util.List;

public record AiTagsResponse(
    List<String> musicTags,
    List<String> secondaryTags
) {}