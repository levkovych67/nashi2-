package com.nashi.artist;

import java.util.function.Predicate;

/** Picks a free slug: the base slug of the name, then {@code base-2}, {@code base-3}, … */
public final class SlugAllocator {

    private SlugAllocator() {
    }

    public static String allocate(String name, Predicate<String> taken) {
        String base = SlugGenerator.slugify(name);
        String candidate = base;
        for (int n = 2; taken.test(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
