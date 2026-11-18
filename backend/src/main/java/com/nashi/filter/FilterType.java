package com.nashi.filter;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum FilterType {
    MULTI_SELECT, SINGLE_SELECT, RANGE, BOOLEAN
}
