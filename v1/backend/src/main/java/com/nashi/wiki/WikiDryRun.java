package com.nashi.wiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Collects artists from the live Wikimedia APIs without Spring, the database or Telegram, and writes them to
 * {@code build/wiki-dry-run.json}. Run with {@code ./gradlew wikiDryRun}; add {@code -Plimit=50} for a quick look at
 * the first 50 candidates of each group (musicians, ensembles, visual artists, writers), as the local seeding does.
 */
public final class WikiDryRun {

    static final int CHUNK = 200;

    private WikiDryRun() {
    }

    public static void main(String[] args) throws IOException {
        int limit = args.length >= 2 && "--limit".equals(args[0]) ? Integer.parseInt(args[1]) : 0;
        WikiCollector collector = new WikiCollector(new PoliteWikiHttp(), new MusicBrainzClient());

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

        Path out = Path.of("build", "wiki-dry-run.json");
        Files.createDirectories(out.getParent());
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        Files.writeString(out, gson.toJson(artists), StandardCharsets.UTF_8);

        stats(artists, collector.russiaLinkedExcluded()).forEach(System.out::println);
        System.out.println("written to " + out.toAbsolutePath());
    }

    /** What the run found, one line per figure. */
    static List<String> stats(List<WikiArtistData> artists, int russiaLinked) {
        Map<ArtistCategory, Integer> perCategory = zeros();
        Map<ArtistCategory, Integer> withWorks = zeros();
        Map<LocationBasis, Integer> perBasis = new EnumMap<>(LocationBasis.class);
        for (LocationBasis basis : LocationBasis.values()) {
            perBasis.put(basis, 0);
        }
        Map<SocialLink.Platform, Integer> perPlatform = new EnumMap<>(SocialLink.Platform.class);
        for (SocialLink.Platform platform : SocialLink.Platform.values()) {
            perPlatform.put(platform, 0);
        }
        Map<Region, Integer> perRegion = new TreeMap<>();
        int withPlace = 0;
        int withBirthDate = 0;
        int withPhoto = 0;
        int withBiography = 0;
        for (WikiArtistData a : artists) {
            for (ArtistCategory category : a.categories()) {
                perCategory.merge(category, 1, Integer::sum);
                if (!a.works().isEmpty()) {
                    withWorks.merge(category, 1, Integer::sum);
                }
            }
            if (a.place() != null) {
                withPlace++;
                perRegion.merge(a.place().region(), 1, Integer::sum);
            }
            if (a.locationBasis() != null) {
                perBasis.merge(a.locationBasis(), 1, Integer::sum);
            }
            withBirthDate += a.birthDate() != null ? 1 : 0;
            withPhoto += a.imageUrl() != null ? 1 : 0;
            withBiography += a.biography() != null ? 1 : 0;
            a.socialLinks().stream().map(SocialLink::getPlatform).filter(Objects::nonNull).distinct()
                    .forEach(platform -> perPlatform.merge(platform, 1, Integer::sum));
        }
        return List.of(
                "assembled: " + artists.size(),
                "per category: " + perCategory,
                "with a place on the map: " + withPlace + ", by basis: " + perBasis,
                "with a birth date: " + withBirthDate,
                "with works: " + withWorks,
                "with a photo: " + withPhoto + ", with a biography: " + withBiography,
                "links per platform: " + perPlatform,
                "excluded as linked to Russia: " + russiaLinked,
                "per region: " + perRegion);
    }

    private static Map<ArtistCategory, Integer> zeros() {
        Map<ArtistCategory, Integer> map = new EnumMap<>(ArtistCategory.class);
        for (ArtistCategory category : ArtistCategory.values()) {
            map.put(category, 0);
        }
        return map;
    }
}
