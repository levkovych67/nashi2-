package com.nashi.wiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nashi.geo.Region;
import org.springframework.boot.convert.DurationStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Collects musicians and bands from the live Wikimedia APIs without Spring, the database or Telegram, and writes them
 * to {@code build/wiki-dry-run.json}; nothing is saved. Run with {@code ./gradlew wikiDryRun}; add {@code -Plimit=50}
 * for the first 50 candidates of each group. The User-Agent and the pause between requests come from the system
 * properties {@code nashi.http.user-agent} and {@code nashi.wiki.request-gap}, with the {@link PoliteWikiHttp}
 * defaults (the project User-Agent of {@code application.properties}, one second).
 */
public final class WikiDryRun {

    static final int CHUNK = 200;
    static final Path OUT = Path.of("build", "wiki-dry-run.json");

    private WikiDryRun() {
    }

    public static void main(String[] args) throws IOException {
        int limit = limit(args);
        String userAgent = System.getProperty("nashi.http.user-agent", PoliteWikiHttp.DEFAULT_USER_AGENT);
        String gap = System.getProperty("nashi.wiki.request-gap");
        Duration requestGap = gap == null || gap.isBlank() ? PoliteWikiHttp.DEFAULT_REQUEST_GAP
                : DurationStyle.detectAndParse(gap);
        System.out.println("User-Agent: " + userAgent + ", gap between requests: " + requestGap);
        WikiCollector collector = new WikiCollector(new PoliteWikiHttp(userAgent, requestGap),
                new MusicBrainzClient(userAgent));

        List<WikiCandidate> candidates = collector.listCandidates();
        System.out.println("candidates: " + candidates.size() + ", failed candidate queries: "
                + collector.failedCandidateChunks());
        List<WikiCandidate> wanted = WikiCandidate.limitPerGroup(candidates, limit);
        if (limit > 0) {
            System.out.println("checking " + wanted.size() + ", at most " + limit + " per group");
        }

        List<WikiArtistData> artists = new ArrayList<>();
        int checked = 0;
        for (List<WikiCandidate> chunk : WikiCollector.batches(wanted, CHUNK)) {
            artists.addAll(collector.fetchDetails(chunk));
            checked += chunk.size();
            System.out.println("assembled " + artists.size() + " of " + checked + " checked (" + wanted.size()
                    + " to check)");
        }

        Files.createDirectories(OUT.getParent());
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Files.writeString(OUT, gson.toJson(artists), StandardCharsets.UTF_8);

        stats(wanted, artists, collector.russiaLinkedExcluded()).forEach(System.out::println);
        System.out.println("written to " + OUT.toAbsolutePath());
    }

    /** {@code --limit N}: at most N candidates per group; 0 (no argument) for all. */
    static int limit(String[] args) {
        return args.length >= 2 && "--limit".equals(args[0]) ? Integer.parseInt(args[1]) : 0;
    }

    /** What the run found, one line per figure. "On the map" applies the import's origin rule. */
    static List<String> stats(List<WikiCandidate> checked, List<WikiArtistData> artists, int russiaLinked) {
        Map<CandidateGroup, Integer> perGroup = zeros(CandidateGroup.class);
        checked.forEach(c -> perGroup.merge(c.group(), 1, Integer::sum));
        Map<WikiCategory, Integer> perCategory = zeros(WikiCategory.class);
        Map<LocationBasis, Integer> perBasis = zeros(LocationBasis.class);
        Map<WikiLink.Platform, Integer> perPlatform = zeros(WikiLink.Platform.class);
        Map<Region, Integer> perRegion = new TreeMap<>();
        int withPlace = 0;
        int onMap = 0;
        int withTagline = 0;
        int withBirthDate = 0;
        int withAlbums = 0;
        int withPhoto = 0;
        int withBiography = 0;
        int withChannel = 0;
        for (WikiArtistData a : artists) {
            a.categories().forEach(category -> perCategory.merge(category, 1, Integer::sum));
            if (a.place() != null) {
                withPlace++;
                perBasis.merge(a.locationBasis(), 1, Integer::sum);
            }
            WikiPlace origin = WikiMusicianMapper.originPlace(a);
            if (origin != null) {
                onMap++;
                perRegion.merge(origin.region(), 1, Integer::sum);
            }
            withTagline += a.tagline() != null ? 1 : 0;
            withBirthDate += a.birthDate() != null ? 1 : 0;
            withAlbums += a.works().isEmpty() ? 0 : 1;
            withPhoto += a.imageUrl() != null ? 1 : 0;
            withBiography += a.biography() != null ? 1 : 0;
            withChannel += a.youtubeChannelUrl() != null ? 1 : 0;
            a.socialLinks().stream().map(WikiLink::getPlatform).filter(Objects::nonNull).distinct()
                    .forEach(platform -> perPlatform.merge(platform, 1, Integer::sum));
        }
        return List.of(
                "checked per group: " + perGroup,
                "assembled: " + artists.size(),
                "per category: " + perCategory,
                "with a place in Ukraine: " + withPlace + " (" + percent(withPlace, artists.size()) + "), by basis: "
                        + perBasis,
                "on the map (birth place of a person, formation place of a band): " + onMap + " ("
                        + percent(onMap, artists.size()) + ")",
                "per region on the map: " + perRegion,
                "with a tagline: " + withTagline,
                "with a birth date: " + withBirthDate,
                "with albums: " + withAlbums,
                "with a photo: " + withPhoto + ", with a biography: " + withBiography,
                "with a YouTube channel: " + withChannel,
                "links per platform: " + perPlatform,
                "excluded as linked to Russia: " + russiaLinked);
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "0%" : Math.round(100.0 * part / whole) + "%";
    }

    private static <E extends Enum<E>> Map<E, Integer> zeros(Class<E> type) {
        Map<E, Integer> map = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            map.put(value, 0);
        }
        return map;
    }
}
