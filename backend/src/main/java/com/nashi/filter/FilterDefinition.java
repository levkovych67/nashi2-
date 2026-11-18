package com.nashi.filter;

import java.util.List;

public record FilterDefinition(String key, String label, FilterType type, List<FilterOption> options) {
}
