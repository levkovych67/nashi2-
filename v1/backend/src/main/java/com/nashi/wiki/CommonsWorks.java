package com.nashi.wiki;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works of a visual artist from their Wikimedia Commons category (P373), for the many artists Wikidata knows no works
 * of. The files come from the category's own «Paintings by …», «Works by …» and similar subcategories; the person's name
 * in those titles is not checked, since the subcategory already sits in the artist's category.
 */
public final class CommonsWorks {

    static final List<String> WORKS_PREFIXES = List.of("Paintings by ", "Works by ", "Drawings by ", "Sculptures by ",
            "Prints by ", "Artworks by ", "Graphic works by ");

    private static final String API = "https://commons.wikimedia.org/w/api.php";
    private static final String CATEGORY = "Category:";
    private static final int MAX_SUBCATEGORIES = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "tif", "tiff", "webp");
    private static final Pattern EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{2,5}$");
    private static final Pattern TRAILING_PARENTHESES = Pattern.compile("^(.*?)\\s*\\(([^()]*)\\)$");
    /** «1959», «1907-08», «1907–1908», «c. 1900», «1890s»: a date belongs to the title. */
    private static final Pattern DATE = Pattern.compile(
            "(?i)(c\\.|ca\\.|circa|бл\\.|близько)?\\s*\\d{4}s?(\\s*[-–—]\\s*\\d{2,4})?");
    private static final int MIN_TITLE = 3;

    private final Function<String, String> get;

    /** @param get fetches a Commons API URL and returns the JSON body */
    public CommonsWorks(Function<String, String> get) {
        this.get = get;
    }

    /**
     * Up to {@code max} image files (names without "File:") of the works subcategories of {@code category}, in the
     * order Commons lists the subcategories and their files. One request lists the subcategories, then one per works
     * subcategory until {@code max} files are found.
     *
     * @param category the P373 value, with or without "Category:"
     */
    public List<String> files(String category, int max) {
        if (max <= 0 || category == null || category.isBlank()) {
            return List.of();
        }
        List<String> subcategories = MediaWikiParser.parseCategoryMembers(get.apply(members(
                        withNamespace(category), "subcat", MAX_SUBCATEGORIES))).stream()
                .filter(CommonsWorks::isWorksCategory)
                .toList();
        Set<String> files = new LinkedHashSet<>();
        for (String subcategory : subcategories) {
            if (files.size() >= max) {
                break;
            }
            for (String title : MediaWikiParser.parseCategoryMembers(get.apply(members(subcategory, "file",
                    max - files.size())))) {
                String file = MediaWikiParser.fileName(title);
                if (files.size() < max && isImage(file)) {
                    files.add(file);
                }
            }
        }
        return List.copyOf(files);
    }

    /** Whether a subcategory holds the artist's works: its title, without "Category:", starts with a works prefix. */
    public static boolean isWorksCategory(String title) {
        if (title == null) {
            return false;
        }
        String name = title.startsWith(CATEGORY) ? title.substring(CATEGORY.length()) : title;
        return WORKS_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /**
     * A title for a work known only by its file: the name without the extension, "_" as spaces, and without a trailing
     * parenthesised museum, inventory or ID suffix ("(Q12345)", "(National Art Museum of Ukraine)") when at least
     * {@value #MIN_TITLE} characters are left. A date in parentheses, such as "(1959)", is part of the title and stays.
     */
    public static String workTitle(String fileName) {
        String title = EXTENSION.matcher(fileName.trim()).replaceFirst("").replace('_', ' ')
                .replaceAll("\\s+", " ").trim();
        Matcher suffix = TRAILING_PARENTHESES.matcher(title);
        if (suffix.matches() && !DATE.matcher(suffix.group(2).trim()).matches()
                && suffix.group(1).trim().length() >= MIN_TITLE) {
            return suffix.group(1).trim();
        }
        return title;
    }

    private static boolean isImage(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 && IMAGE_EXTENSIONS.contains(file.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String withNamespace(String category) {
        String name = category.trim();
        return name.startsWith(CATEGORY) ? name : CATEGORY + name;
    }

    private static String members(String categoryTitle, String type, int limit) {
        return API + "?action=query&format=json&formatversion=2&list=categorymembers&cmtype=" + type
                + "&cmlimit=" + limit + "&cmtitle=" + URLEncoder.encode(categoryTitle, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
